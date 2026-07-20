#pragma once

#include <cstddef>
#include <complex>
#include <vector>

namespace app::ctranslate2_jni {

/** モデル単位で再利用するWhisper feature抽出作業領域。 */
struct WhisperFeatureWorkspace {
  std::vector<float> audio;
  std::vector<float> power;
  std::vector<float> hann;
  std::vector<float> custom_filters;
  size_t custom_filter_mels = 0;
  std::vector<std::complex<float>> fft_input;
  std::vector<std::complex<float>> fft_output;
  std::vector<std::complex<float>> fft_scratch;
};

/**
 * モデルのmel数に合わせて作業領域を確保する。
 * n_mels例: 80、workspace例: WhisperHandle所有領域。戻り値なし。
 * n_melsが0の場合はstd::invalid_argumentを送出する。
 */
void prepare_whisper_feature_workspace(
    size_t n_mels,
    WhisperFeatureWorkspace& workspace);

/**
 * 16kHz PCMをWhisper互換log-Melへ変換し、呼び出し側outputへ書き込む。
 * samples例: 80000要素、n_mels例: 80、output例: 再利用vector、
 * workspace例: WhisperHandle所有領域。戻り値なし。n_melsが0なら例外を送出する。
 */
void make_whisper_features(
    const std::vector<float>& samples,
    size_t n_mels,
    std::vector<float>& output,
    WhisperFeatureWorkspace& workspace);

/**
 * thread単位workspaceを使う互換API。samples例: 80000要素、n_mels例: 80、
 * output例: 再利用vector。戻り値なし。n_melsが0なら例外を送出する。
 */
void make_whisper_features(
    const std::vector<float>& samples,
    size_t n_mels,
    std::vector<float>& output);

}  // namespace app::ctranslate2_jni
