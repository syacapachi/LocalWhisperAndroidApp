#include "whisper-feature-extractor.h"

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <complex>
#include <stdexcept>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define APP_FEATURE_HAS_NEON 1
#else
#define APP_FEATURE_HAS_NEON 0
#endif

#include "cpu/parallel.h"

namespace app::ctranslate2_jni {
namespace {

// Whisperが前提とする入力音声のサンプリング周波数（Hz）。
constexpr size_t kSampleRate = 16000;
// Whisperの短時間フーリエ変換で使用するFFT点数。
constexpr size_t kFftSize = 400;
// reflect paddingの中心および片側FFT窓幅。
constexpr size_t kFftHalfSize = 200;
// 実数入力FFTで使用する非負周波数bin数（N/2+1）。
constexpr size_t kFftBins = 201;
// 隣接するFFTフレーム間の移動量（10ms、160サンプル）。
constexpr size_t kHopLength = 160;
// 1回のfeature抽出で扱う最大音声長（30秒）。
constexpr size_t kChunkSamples = 30 * kSampleRate;
// 30秒分のWhisper入力時間フレーム数。
constexpr size_t kFrames = 3000;
// CTranslate2スレッドプールへ割り当てる1タスク当たりの最小フレーム数。
constexpr size_t kFeatureParallelGrainFrames = 16;
// 128-bit NEONレジスタで同時処理できるfloat要素数。
constexpr size_t kNeonFloatLanes = 4;
// FFT回転係数とHann窓の計算に使用する円周率。
constexpr float kPi = 3.14159265358979323846f;
// float演算でFFT点数による除算を乗算へ置き換える逆数。
constexpr float kInvFftSizeFloat = 1.0f / static_cast<float>(kFftSize);
// double演算でFFT点数による除算を乗算へ置き換える逆数。
constexpr double kInvFftSizeDouble = 1.0 / static_cast<double>(kFftSize);
// Nyquist周波数など半分の値を求めるための係数。
constexpr double kHalf = 0.5;
// 1000Hz未満の線形mel領域における1 mel当たりの周波数幅。
constexpr double kHzPerLinearMel = 66.6666666666666667;
// Hzから線形melへ変換するための周波数幅の逆数。
constexpr double kInvHzPerLinearMel = 1.0 / kHzPerLinearMel;
// 線形melと対数melを切り替える境界周波数（Hz）。
constexpr double kMinLogHz = 1000.0;
// 対数mel計算で周波数を境界周波数に対して正規化するための逆数。
constexpr double kInvMinLogHz = 1.0 / kMinLogHz;
// 1000Hzに対応するmel値で、線形領域と対数領域の接続点。
constexpr double kMinLogMel = 15.0;
// Whisper互換mel尺度の対数領域における増加幅。
constexpr double kMelLogStep = 0.0687517774209491;
// 対数値からmel値を求める際に除算を置き換える増加幅の逆数。
constexpr double kInvMelLogStep = 1.0 / kMelLogStep;
// 80 melフィルタを両端込み81区間へ等分するときの区間数の逆数。
constexpr double kInvMel81Intervals = 1.0 / 81.0;
// 128 melフィルタを両端込み129区間へ等分するときの区間数の逆数。
constexpr double kInvMel129Intervals = 1.0 / 129.0;
// log10(0)を防ぐためmel energyへ適用する下限値。
constexpr float kMinimumMelEnergy = 1e-10f;
// 音声が存在しない未使用フレームへ設定する初期log-Mel値。
constexpr float kInitialLogMel = -10.0f;
// Whisper前処理で保持する最大log-Mel値からのダイナミックレンジ。
constexpr float kDynamicRange = 8.0f;
// Whisper入力のlog-Mel正規化前に加えるオフセット。
constexpr float kNormalizationOffset = 4.0f;
// Whisper入力のlog-Mel正規化で4除算を乗算へ置き換える係数。
constexpr float kNormalizationScale = 0.25f;

using FftRoots = std::array<std::complex<float>, kFftSize>;

/**
 * 固定400点mixed-radix FFTの因数を返す。
 * n例: 25。戻り値例: 5。400点FFTから到達しないnではnを返し、例外なし。
 */
constexpr size_t fft_factor(const size_t n) noexcept {
  return n == 400 || n == 200 || n == 100 || n == 50
      ? 2
      : n == 25 ? 5 : n;
}

/**
 * 固定400点FFTの分割後サイズを整数除算なしで返す。
 * n例: 100。戻り値例: 50。未対応値では0を返し、例外なし。
 */
constexpr size_t fft_part_size(const size_t n) noexcept {
  return n == 400 ? 200
       : n == 200 ? 100
       : n == 100 ? 50
       : n == 50 ? 25
       : n == 25 ? 5
       : 0;
}

/**
 * n点FFTの回転係数を400点テーブルで引くstrideを返す。
 * n例: 100。戻り値例: 4。未対応値では0を返し、例外なし。
 */
constexpr size_t fft_root_stride(const size_t n) noexcept {
  return n == 400 ? 1
       : n == 200 ? 2
       : n == 100 ? 4
       : n == 50 ? 8
       : n == 25 ? 16
       : n == 5 ? 80
       : 0;
}

/**
 * 400点FFTで再利用する単位円上の回転係数を返す。
 * 引数なし。戻り値例: roots[0] == {1, 0}。初期化時のメモリ確保失敗以外は例外なし。
 */
const FftRoots& fft_roots() {
  static const FftRoots roots = [] {
    FftRoots values{};
    for (size_t index = 0; index < values.size(); ++index) {
      const float angle = -2.0f * kPi * static_cast<float>(index)
                          * kInvFftSizeFloat;
      values[index] = {std::cos(angle), std::sin(angle)};
    }
    return values;
  }();
  return roots;
}

/**
 * n点FFT用の回転係数を400点の事前計算表から取得する。
 * n例: 200、exponent例: 3。戻り値例: exp(-2πi*3/200)。nが400の因数でない場合は未定義。
 */
inline const std::complex<float>& fft_root(
  const size_t n,
  const size_t exponent) noexcept {
  const size_t root_index = ((exponent % n) * fft_root_stride(n)) % kFftSize;
  return fft_roots()[root_index];
}

/**
 * mixed-radix FFTを実行する。
 * input例: 400複素サンプル、input_stride例: 1、output/scratch例: 各400要素、n例: 400。
 * 戻り値なし。各ポインタの領域不足時は未定義で、例外は送出しない。
 */
void fft(
    const std::complex<float>* input,
    const size_t input_stride,
    std::complex<float>* output,
    std::complex<float>* scratch,
    const size_t n) {
  if (n <= 1) {
    if (n == 1)
      output[0] = input[0];
    return;
  }
  const size_t factor = fft_factor(n);
  if (factor == n) {
    for (size_t k = 0; k < n; ++k) {
      output[k] = {};
      for (size_t t = 0; t < n; ++t)
        output[k] += input[t * input_stride] * fft_root(n, k * t);
    }
    return;
  }

  const size_t part_size = fft_part_size(n);
  for (size_t part = 0; part < factor; ++part) {
    fft(input + part * input_stride,
        input_stride * factor,
        scratch + part * part_size,
        output + part * part_size,
        part_size);
  }

  for (size_t k = 0; k < n; ++k) {
    output[k] = {};
    for (size_t part = 0; part < factor; ++part) {
      output[k] += scratch[part * part_size + k % part_size]
                   * fft_root(n, part * k);
    }
  }
}

/** 周波数frequency例: 1000Hzをmelへ変換する。戻り値例: 15。例外なし。 */
constexpr double hz_to_mel(const double frequency) noexcept {
  if (frequency < kMinLogHz)
    return frequency * kInvHzPerLinearMel;
  return kMinLogMel
         + std::log(frequency * kInvMinLogHz) * kInvMelLogStep;
}

/** mel値mel例: 15をHzへ変換する。戻り値例: 1000Hz。例外なし。 */
constexpr double mel_to_hz(const double mel) noexcept {
  if (mel < kMinLogMel)
    return mel * kHzPerLinearMel;
  return kMinLogHz * std::exp(kMelLogStep * (mel - kMinLogMel));
}

/**
 * Whisper互換melフィルタを生成する。
 * n_mels例: 80。戻り値例: 80*201要素の係数。メモリ確保失敗時はstd::bad_alloc。
 */
std::vector<float> make_mel_filters(const size_t n_mels) {
  std::vector<double> frequencies(n_mels + 2);
  static const double kMaxMel = hz_to_mel(kSampleRate * kHalf);
  const double inv_frequency_intervals = n_mels == 80
      ? kInvMel81Intervals
      : n_mels == 128
          ? kInvMel129Intervals
          : 1.0 / static_cast<double>(n_mels + 1);
  for (size_t i = 0; i < frequencies.size(); ++i)
    frequencies[i] = mel_to_hz(
        kMaxMel * static_cast<double>(i) * inv_frequency_intervals);

  std::vector<float> filters(n_mels * kFftBins);
  for (size_t mel = 0; mel < n_mels; ++mel) {
    const double inv_mel_span = 1.0 / (frequencies[mel + 2] - frequencies[mel]);
    const double inv_lower_span = 1.0 / (frequencies[mel + 1] - frequencies[mel]);
    const double inv_upper_span = 1.0 / (frequencies[mel + 2] - frequencies[mel + 1]);
    const double normalization = 2.0 * inv_mel_span;
    for (size_t bin = 0; bin < kFftBins; ++bin) {
      const double hz = static_cast<double>(bin * kSampleRate) * kInvFftSizeDouble;
      const double lower = (hz - frequencies[mel]) * inv_lower_span;
      const double upper = (frequencies[mel + 2] - hz) * inv_upper_span;
      filters[mel * kFftBins + bin] = static_cast<float>(
          normalization * std::max(0.0, std::min(lower, upper)));
    }
  }
  return filters;
}

/**
 * 配列端をreflect paddingして音声サンプルを読む。
 * audio例: {1,2,3}、index例: -1。戻り値例: 2。例外なし。
 */
float reflected_sample(const std::vector<float>& audio, long index) noexcept {
  const long size = static_cast<long>(audio.size());
  if (size <= 1)
    return size == 1 ? audio[0] : 0.0f;
  while (index < 0 || index >= size)
    index = index < 0 ? -index : 2 * size - index - 2;
  return audio[static_cast<size_t>(index)];
}

/**
 * 1フレームへHann窓を掛け、FFT入力を作る。__ARM_NEON__が有効な場合並列実行
 * audio/hann例: 16kHz音声と400係数、start例: 160、output例: 400複素要素。
 * 戻り値なし。outputが400要素未満の場合は未定義で、例外なし。
 */
void make_windowed_frame(
    const std::vector<float>& audio,
    const std::vector<float>& hann,
    const long start,
    std::complex<float>* output) noexcept {
  size_t index = 0;
  const bool is_contiguous = start >= 0
      && start + static_cast<long>(kFftSize) <= static_cast<long>(audio.size());
#if APP_FEATURE_HAS_NEON
  if (is_contiguous) {
    const float* source = audio.data() + static_cast<size_t>(start);
    // 4つずつ実行
    for (; index + kNeonFloatLanes <= kFftSize; index += kNeonFloatLanes) {
      float32x4x2_t complex_values;
      // 音声データとハミング窓を掛け、複素数化
      complex_values.val[0] = vmulq_f32(
          vld1q_f32(source + index), vld1q_f32(hann.data() + index));
      complex_values.val[1] = vdupq_n_f32(0.0f);
      vst2q_f32(reinterpret_cast<float*>(output + index), complex_values);
    }
  }
#endif
  if (is_contiguous) {
    for (; index < kFftSize; ++index)
      output[index] = audio[static_cast<size_t>(start) + index] * hann[index];
  } else {
    for (; index < kFftSize; ++index)
      output[index] = reflected_sample(audio, start + static_cast<long>(index)) * hann[index];
  }
}

/**
 * 複素FFT結果から先頭201 binのpowerを算出する。__ARM_NEON__が有効な場合並列実行
 * fft_output例: 400複素要素、power例: 201要素。戻り値なし。領域不足時は未定義、例外なし。
 */
void calculate_power(
    const std::complex<float>* fft_output,
    float* power) noexcept {
  static_assert(sizeof(std::complex<float>) == sizeof(float) * 2,
                "NEON path requires interleaved complex<float>");
  size_t bin = 0;
#if APP_FEATURE_HAS_NEON
  // 4つずつ実行
  for (; bin + kNeonFloatLanes <= kFftBins; bin += kNeonFloatLanes) {
    const float32x4x2_t values = vld2q_f32(
        reinterpret_cast<const float*>(fft_output + bin));
    vst1q_f32(power + bin,
              vmlaq_f32(vmulq_f32(values.val[0], values.val[0]),
                         values.val[1], values.val[1]));
  }
#endif
  for (; bin < kFftBins; ++bin)
    power[bin] = std::norm(fft_output[bin]);
}

/**
 * 2つのfloat配列の内積を返す。__ARM_NEON__が有効な場合並列実行
 * left例: mel係数201要素、right例: power 201要素、count例: 201。
 * 戻り値例: mel energy。領域不足時は未定義、例外なし。
 */
float dot_product(
    const float* left,
    const float* right,
    const size_t count) noexcept {
  size_t index = 0;
  float result = 0.0f;
#if APP_FEATURE_HAS_NEON
  float32x4_t sum = vdupq_n_f32(0.0f);
  // 4つずつ実行
  for (; index + kNeonFloatLanes <= count; index += kNeonFloatLanes)
    sum = vmlaq_f32(sum, vld1q_f32(left + index), vld1q_f32(right + index));
  const float32x2_t pair_sum = vadd_f32(vget_low_f32(sum), vget_high_f32(sum));
  result = vget_lane_f32(vpadd_f32(pair_sum, pair_sum), 0);
#endif
  for (; index < count; ++index)
    result += left[index] * right[index];
  return result;
}

/**
 * log-Mel配列をWhisper入力範囲へ正規化する。__ARM_NEON__が有効な場合並列実行
 * values例: {-10,-2}、maximum例: -2。戻り値なし。例外なし。
 */
void normalize_features(std::vector<float>& values, const float maximum) noexcept {
  const float floor = maximum - kDynamicRange;
  size_t index = 0;
#if APP_FEATURE_HAS_NEON
  const float32x4_t floor_vector = vdupq_n_f32(floor);
  const float32x4_t offset = vdupq_n_f32(kNormalizationOffset);
  const float32x4_t scale = vdupq_n_f32(kNormalizationScale);
  for (; index + kNeonFloatLanes <= values.size(); index += kNeonFloatLanes) {
    float32x4_t value = vmaxq_f32(vld1q_f32(values.data() + index), floor_vector);
    value = vmulq_f32(vaddq_f32(value, offset), scale);
    vst1q_f32(values.data() + index, value);
  }
#endif
  for (; index < values.size(); ++index)
    values[index] = (std::max(values[index], floor) + kNormalizationOffset)
                    * kNormalizationScale;
}

}  // namespace

void prepare_whisper_feature_workspace(
    const size_t n_mels,
    WhisperFeatureWorkspace& workspace,
    const size_t num_threads) {
  if (n_mels == 0)
    throw std::invalid_argument("n_mels must be greater than 0");
  if (num_threads > 0)
    workspace.num_threads = num_threads;
  workspace.audio.resize(kChunkSamples);
  workspace.thread_workspaces.resize(workspace.num_threads);
  workspace.thread_maxima.resize(workspace.num_threads);
  for (WhisperFeatureThreadWorkspace& thread_workspace : workspace.thread_workspaces) {
    thread_workspace.power.resize(kFftBins);
    thread_workspace.fft_input.resize(kFftSize);
    thread_workspace.fft_output.resize(kFftSize);
    thread_workspace.fft_scratch.resize(kFftSize);
  }
  if (workspace.hann.size() != kFftSize) {
    workspace.hann.resize(kFftSize);
    for (size_t index = 0; index < kFftSize; ++index) {
      workspace.hann[index] = 0.5f - 0.5f * std::cos(
          2.0f * kPi * static_cast<float>(index) * kInvFftSizeFloat);
    }
  }
  if (n_mels != 80 && n_mels != 128
      && workspace.custom_filter_mels != n_mels) {
    workspace.custom_filters = make_mel_filters(n_mels);
    workspace.custom_filter_mels = n_mels;
  }
}

void make_whisper_features(
    const std::vector<float>& samples,
    const size_t n_mels,
    std::vector<float>& output,
    WhisperFeatureWorkspace& workspace) {
  prepare_whisper_feature_workspace(n_mels, workspace);

  std::fill(workspace.audio.begin(), workspace.audio.end(), 0.0f);
  const size_t copied_samples = std::min(samples.size(), workspace.audio.size());
  std::copy_n(samples.begin(), copied_samples, workspace.audio.begin());
  static const std::vector<float> kFilters80 = make_mel_filters(80);
  static const std::vector<float> kFilters128 = make_mel_filters(128);
  const std::vector<float>* filters = nullptr;
  if (n_mels == 80)
    filters = &kFilters80;
  else if (n_mels == 128)
    filters = &kFilters128;
  else
    filters = &workspace.custom_filters;

  output.resize(n_mels * kFrames);
  std::fill(output.begin(), output.end(), kInitialLogMel);
  const size_t active_frames = std::min(
      kFrames,
      (copied_samples + kFftHalfSize + kHopLength - 1) / kHopLength);
  std::fill(workspace.thread_maxima.begin(), workspace.thread_maxima.end(), kInitialLogMel);
  std::atomic_size_t next_workspace{0};
#ifdef _OPENMP
  omp_set_num_threads(static_cast<int>(workspace.num_threads));
#else
  ctranslate2::cpu::set_num_threads(workspace.num_threads);
#endif

  ctranslate2::cpu::parallel_for(
      0,
      active_frames,
      kFeatureParallelGrainFrames,
      [&](const ctranslate2::dim_t begin, const ctranslate2::dim_t end) {
        const size_t workspace_index = next_workspace.fetch_add(1, std::memory_order_relaxed);
        WhisperFeatureThreadWorkspace& thread_workspace =
            workspace.thread_workspaces[workspace_index];
        float local_maximum = kInitialLogMel;
        for (size_t frame = begin; frame < end; ++frame) {
          const long start = static_cast<long>(frame * kHopLength)
                             - static_cast<long>(kFftHalfSize);
          make_windowed_frame(
              workspace.audio,
              workspace.hann,
              start,
              thread_workspace.fft_input.data());
          fft(thread_workspace.fft_input.data(),
              1,
              thread_workspace.fft_output.data(),
              thread_workspace.fft_scratch.data(),
              kFftSize);
          calculate_power(
              thread_workspace.fft_output.data(), thread_workspace.power.data());

          for (size_t mel = 0; mel < n_mels; ++mel) {
            const float energy = dot_product(
                filters->data() + mel * kFftBins,
                thread_workspace.power.data(),
                kFftBins);
            const float value = std::log10(std::max(energy, kMinimumMelEnergy));
            output[mel * kFrames + frame] = value;
            local_maximum = std::max(local_maximum, value);
          }
        }
        workspace.thread_maxima[workspace_index] = local_maximum;
      });

  const float maximum = *std::max_element(
      workspace.thread_maxima.begin(),
      workspace.thread_maxima.begin()
          + static_cast<std::ptrdiff_t>(next_workspace.load(std::memory_order_relaxed)));
  normalize_features(output, maximum);
}

void make_whisper_features(
    const std::vector<float>& samples,
    const size_t n_mels,
    std::vector<float>& output) {
  thread_local WhisperFeatureWorkspace workspace;
  make_whisper_features(samples, n_mels, output, workspace);
}

}  // namespace app::ctranslate2_jni
