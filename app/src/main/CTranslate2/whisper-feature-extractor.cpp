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

std::vector<std::complex<float>> fft(const std::vector<std::complex<float>>& input) {
  const size_t n = input.size();
  if (n <= 1)
    return input;
  const size_t factor = smallest_factor(n);
  if (factor == n) {
    std::vector<std::complex<float>> output(n);
    for (size_t k = 0; k < n; ++k) {
      for (size_t t = 0; t < n; ++t) {
        const float angle = -2.0f * kPi * static_cast<float>(k * t) / static_cast<float>(n);
        output[k] += input[t] * std::polar(1.0f, angle);
      }
    }
    return output;
  }

  const size_t part_size = n / factor;
  std::vector<std::vector<std::complex<float>>> parts(factor);
  for (size_t part = 0; part < factor; ++part) {
    std::vector<std::complex<float>> values(part_size);
    for (size_t i = 0; i < part_size; ++i)
      values[i] = input[part + i * factor];
    parts[part] = fft(values);
  }

  std::vector<std::complex<float>> output(n);
  for (size_t k = 0; k < n; ++k) {
    for (size_t part = 0; part < factor; ++part) {
      const float angle = -2.0f * kPi * static_cast<float>(part * k) / static_cast<float>(n);
      output[k] += parts[part][k % part_size] * std::polar(1.0f, angle);
    }
  }
  return output;
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

std::vector<float> make_whisper_features(const std::vector<float>& samples, size_t n_mels) {
  if (n_mels == 0)
    throw std::invalid_argument("n_mels must be greater than 0");

  std::vector<float> audio(kChunkSamples, 0.0f);
  const size_t copied_samples = std::min(samples.size(), audio.size());
  std::copy_n(samples.begin(), copied_samples, audio.begin());
  static const auto filters80 = make_mel_filters(80);
  static const auto filters128 = make_mel_filters(128);
  std::vector<float> custom_filters;
  const std::vector<float>* filters = nullptr;
  if (n_mels == 80)
    filters = &filters80;
  else if (n_mels == 128)
    filters = &filters128;
  else {
    custom_filters = make_mel_filters(n_mels);
    filters = &custom_filters;
  }
  const size_t bins = kFftSize / 2 + 1;
  std::vector<float> features(n_mels * kFrames, -10.0f);
  std::vector<float> power(bins);
  float maximum = -10.0f;
  const size_t active_frames = std::min(
      kFrames,
      (copied_samples + kFftSize / 2 + kHopLength - 1) / kHopLength);

  for (size_t frame = 0; frame < active_frames; ++frame) {
    std::vector<std::complex<float>> window(kFftSize);
    const long start = static_cast<long>(frame * kHopLength) - static_cast<long>(kFftSize / 2);
    for (size_t i = 0; i < kFftSize; ++i) {
      const float hann = 0.5f - 0.5f * std::cos(2.0f * kPi * static_cast<float>(i) / kFftSize);
      window[i] = reflected_sample(audio, start + static_cast<long>(i)) * hann;
    }
    const auto spectrum = fft(window);
    for (size_t bin = 0; bin < bins; ++bin)
      power[bin] = std::norm(spectrum[bin]);

    for (size_t mel = 0; mel < n_mels; ++mel) {
      float energy = 0.0f;
      for (size_t bin = 0; bin < bins; ++bin)
        energy += (*filters)[mel * bins + bin] * power[bin];
      const float value = std::log10(std::max(energy, 1e-10f));
      features[mel * kFrames + frame] = value;
      maximum = std::max(maximum, value);
    }
  }

  for (float& value : features)
    value = (std::max(value, maximum - 8.0f) + 4.0f) / 4.0f;
  return features;
}

}  // namespace app::ctranslate2_jni
