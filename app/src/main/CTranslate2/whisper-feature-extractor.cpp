#include "whisper-feature-extractor.h"

#include <algorithm>
#include <cmath>
#include <complex>
#include <stdexcept>

namespace app::ctranslate2_jni {
namespace {

constexpr size_t kSampleRate = 16000;
constexpr size_t kFftSize = 400;
constexpr size_t kHopLength = 160;
constexpr size_t kChunkSamples = 30 * kSampleRate;
constexpr size_t kFrames = 3000;
constexpr float kPi = 3.14159265358979323846f;

size_t smallest_factor(size_t value) {
  for (size_t factor = 2; factor * factor <= value; ++factor) {
    if (value % factor == 0)
      return factor;
  }
  return value;
}

void fft(
    const std::complex<float>* input,
    size_t input_stride,
    std::complex<float>* output,
    std::complex<float>* scratch,
    size_t n) {
  if (n <= 1) {
    if (n == 1)
      output[0] = input[0];
    return;
  }
  const size_t factor = smallest_factor(n);
  if (factor == n) {
    for (size_t k = 0; k < n; ++k) {
      output[k] = {};
      for (size_t t = 0; t < n; ++t) {
        const float angle = -2.0f * kPi * static_cast<float>(k * t) / static_cast<float>(n);
        output[k] += input[t * input_stride] * std::polar(1.0f, angle);
      }
    }
    return;
  }

  const size_t part_size = n / factor;
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
      const float angle = -2.0f * kPi * static_cast<float>(part * k) / static_cast<float>(n);
      output[k] += scratch[part * part_size + k % part_size]
                   * std::polar(1.0f, angle);
    }
  }
}

double hz_to_mel(double frequency) {
  constexpr double min_log_hz = 1000.0;
  constexpr double min_log_mel = min_log_hz / (200.0 / 3.0);
  const double log_step = std::log(6.4) / 27.0;
  if (frequency < min_log_hz)
    return frequency / (200.0 / 3.0);
  return min_log_mel + std::log(frequency / min_log_hz) / log_step;
}

double mel_to_hz(double mel) {
  constexpr double min_log_hz = 1000.0;
  constexpr double min_log_mel = min_log_hz / (200.0 / 3.0);
  const double log_step = std::log(6.4) / 27.0;
  if (mel < min_log_mel)
    return mel * (200.0 / 3.0);
  return min_log_hz * std::exp(log_step * (mel - min_log_mel));
}

std::vector<float> make_mel_filters(size_t n_mels) {
  const size_t bins = kFftSize / 2 + 1;
  std::vector<double> frequencies(n_mels + 2);
  const double max_mel = hz_to_mel(kSampleRate / 2.0);
  for (size_t i = 0; i < frequencies.size(); ++i)
    frequencies[i] = mel_to_hz(max_mel * static_cast<double>(i) / (n_mels + 1));

  std::vector<float> filters(n_mels * bins);
  for (size_t mel = 0; mel < n_mels; ++mel) {
    const double normalization = 2.0 / (frequencies[mel + 2] - frequencies[mel]);
    for (size_t bin = 0; bin < bins; ++bin) {
      const double hz = static_cast<double>(bin * kSampleRate) / kFftSize;
      const double lower = (hz - frequencies[mel]) / (frequencies[mel + 1] - frequencies[mel]);
      const double upper = (frequencies[mel + 2] - hz) / (frequencies[mel + 2] - frequencies[mel + 1]);
      filters[mel * bins + bin] = static_cast<float>(normalization * std::max(0.0, std::min(lower, upper)));
    }
  }
  return filters;
}

float reflected_sample(const std::vector<float>& audio, long index) {
  const long size = static_cast<long>(audio.size());
  if (size <= 1)
    return size == 1 ? audio[0] : 0.0f;
  while (index < 0 || index >= size) {
    index = index < 0 ? -index : 2 * size - index - 2;
  }
  return audio[static_cast<size_t>(index)];
}

}  // namespace

void prepare_whisper_feature_workspace(
    const size_t n_mels,
    WhisperFeatureWorkspace& workspace) {
  if (n_mels == 0)
    throw std::invalid_argument("n_mels must be greater than 0");
  workspace.audio.resize(kChunkSamples);
  workspace.power.resize(kFftSize / 2 + 1);
  workspace.fft_input.resize(kFftSize);
  workspace.fft_output.resize(kFftSize);
  workspace.fft_scratch.resize(kFftSize);
  if (workspace.hann.size() != kFftSize) {
    workspace.hann.resize(kFftSize);
    for (size_t index = 0; index < kFftSize; ++index) {
      workspace.hann[index] = 0.5f - 0.5f * std::cos(
          2.0f * kPi * static_cast<float>(index) / kFftSize);
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
  static const auto filters80 = make_mel_filters(80);
  static const auto filters128 = make_mel_filters(128);
  const std::vector<float>* filters = nullptr;
  if (n_mels == 80)
    filters = &filters80;
  else if (n_mels == 128)
    filters = &filters128;
  else
    filters = &workspace.custom_filters;
  const size_t bins = kFftSize / 2 + 1;
  output.resize(n_mels * kFrames);
  std::fill(output.begin(), output.end(), -10.0f);
  float maximum = -10.0f;
  const size_t active_frames = std::min(
      kFrames,
      (copied_samples + kFftSize / 2 + kHopLength - 1) / kHopLength);

  for (size_t frame = 0; frame < active_frames; ++frame) {
    const long start = static_cast<long>(frame * kHopLength) - static_cast<long>(kFftSize / 2);
    for (size_t i = 0; i < kFftSize; ++i) {
      workspace.fft_input[i] = reflected_sample(
          workspace.audio, start + static_cast<long>(i)) * workspace.hann[i];
    }
    fft(workspace.fft_input.data(), 1, workspace.fft_output.data(),
        workspace.fft_scratch.data(), kFftSize);
    for (size_t bin = 0; bin < bins; ++bin)
      workspace.power[bin] = std::norm(workspace.fft_output[bin]);

    for (size_t mel = 0; mel < n_mels; ++mel) {
      float energy = 0.0f;
      for (size_t bin = 0; bin < bins; ++bin)
        energy += (*filters)[mel * bins + bin] * workspace.power[bin];
      const float value = std::log10(std::max(energy, 1e-10f));
      output[mel * kFrames + frame] = value;
      maximum = std::max(maximum, value);
    }
  }

  for (float& value : output)
    value = (std::max(value, maximum - 8.0f) + 4.0f) / 4.0f;
}

void make_whisper_features(
    const std::vector<float>& samples,
    const size_t n_mels,
    std::vector<float>& output) {
  thread_local WhisperFeatureWorkspace workspace;
  make_whisper_features(samples, n_mels, output, workspace);
}

}  // namespace app::ctranslate2_jni
