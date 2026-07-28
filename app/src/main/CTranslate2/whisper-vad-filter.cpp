#include "whisper-vad-filter.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>

namespace app::ctranslate2_jni {
namespace {

constexpr int kSampleRate = 16000;
constexpr int kSamplesPerCentisecond = kSampleRate / 100;

struct WhisperVadSegmentsDeleter {
  void operator()(whisper_vad_segments* segments) const noexcept {
    whisper_vad_free_segments(segments);
  }
};

using WhisperVadSegmentsPtr =
    std::unique_ptr<whisper_vad_segments, WhisperVadSegmentsDeleter>;

}  // namespace

void WhisperVadContextDeleter::operator()(
    whisper_vad_context* context) const noexcept {
  whisper_vad_free(context);
}

WhisperVadContextPtr create_whisper_vad_context(
    const std::string& model_path) {
  if (model_path.empty())
    throw std::invalid_argument("VAD model path must not be empty");

  whisper_vad_context_params context_params =
      whisper_vad_default_context_params();
  context_params.n_threads = 1;
  context_params.use_gpu = false;
  WhisperVadContextPtr context(
      whisper_vad_init_from_file_with_params(model_path.c_str(), context_params));
  if (!context)
    throw std::runtime_error("failed to load Silero VAD model: " + model_path);
  return context;
}

bool collect_whisper_vad_speech(
    whisper_vad_context* context,
    const std::vector<float>& samples,
    float threshold,
    std::vector<float>& output) {
  if (context == nullptr)
    throw std::invalid_argument("Silero VAD context is null");

  output.clear();
  if (samples.empty())
    return false;

  whisper_vad_params params = whisper_vad_default_params();
  params.threshold = std::clamp(threshold, 0.0f, 1.0f);
  params.min_speech_duration_ms = 0;
  params.max_speech_duration_s = std::numeric_limits<float>::max();
  params.min_silence_duration_ms = 2000;
  params.speech_pad_ms = 400;
  params.samples_overlap = 0.0f;

  WhisperVadSegmentsPtr segments(whisper_vad_segments_from_samples(
      context,
      params,
      samples.data(),
      static_cast<int>(samples.size())));
  if (!segments)
    throw std::runtime_error("Silero VAD failed to detect speech segments");

  const int segment_count =
      whisper_vad_segments_n_segments(segments.get());
  for (int index = 0; index < segment_count; ++index) {
    const auto start_cs =
        whisper_vad_segments_get_segment_t0(segments.get(), index);
    const auto end_cs =
        whisper_vad_segments_get_segment_t1(segments.get(), index);
    const size_t start = std::min(
        samples.size(),
        static_cast<size_t>(std::max(
            0LL,
            std::llround(start_cs * kSamplesPerCentisecond))));
    const size_t end = std::min(
        samples.size(),
        static_cast<size_t>(std::max(
            0LL,
            std::llround(end_cs * kSamplesPerCentisecond))));
    if (end > start)
      output.insert(output.end(), samples.begin() + start, samples.begin() + end);
  }

  return !output.empty();
}

}  // namespace app::ctranslate2_jni
