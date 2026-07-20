#include <jni.h>

#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include <ctranslate2/models/whisper.h>
#include <ctranslate2/storage_view.h>
#include <ctranslate2/types.h>

#include "whisper-feature-extractor.h"
#include "whisper-prompt-tokenizer.h"
#include "whisper-token-decoder.h"
#include "../NativeAudio/pcm16-float-converter.h"

namespace {

struct WhisperHandle {
  std::unique_ptr<ctranslate2::models::Whisper> model;
  std::unique_ptr<app::ctranslate2_jni::WhisperPromptTokenizer> tokenizer;
  std::mutex inference_mutex;
  std::vector<float> pcm_samples;
  std::vector<float> mel_features;
  app::ctranslate2_jni::WhisperFeatureWorkspace feature_workspace;
};

class JStringChars {
public:
  JStringChars(JNIEnv* env, jstring value)
      : _env(env), _value(value), _chars(value == nullptr ? nullptr : env->GetStringUTFChars(value, nullptr)) {
  }

  ~JStringChars() {
    if (_chars != nullptr)
      _env->ReleaseStringUTFChars(_value, _chars);
  }

  std::string str() const {
    return _chars == nullptr ? std::string() : std::string(_chars);
  }

private:
  JNIEnv* _env;
  jstring _value;
  const char* _chars;
};

void throw_java(JNIEnv* env, const char* class_name, const std::string& message) {
  const jclass exception_class = env->FindClass(class_name);
  if (exception_class != nullptr)
    env->ThrowNew(exception_class, message.c_str());
}

WhisperHandle* from_handle(jlong handle) {
  return reinterpret_cast<WhisperHandle*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_CTranslate2_CTranslate2Bridge_create(
    JNIEnv* env,
    jclass,
    jstring model_directory,
    jstring compute_type,
    jint threads) {
  try {
    const JStringChars model_path(env, model_directory);
    const JStringChars compute(env, compute_type);
    if (model_path.str().empty())
      throw std::invalid_argument("modelDirectory must not be empty");
    if (threads <= 0)
      throw std::invalid_argument("threads must be greater than 0");

    ctranslate2::ReplicaPoolConfig config;
    config.num_threads_per_replica = static_cast<size_t>(threads);
    auto handle = std::make_unique<WhisperHandle>();
    handle->model = std::make_unique<ctranslate2::models::Whisper>(
        model_path.str(),
        ctranslate2::Device::CPU,
        ctranslate2::str_to_compute_type(compute.str()),
        std::vector<int>{0},
        false,
        config);
    handle->tokenizer =
        std::make_unique<app::ctranslate2_jni::WhisperPromptTokenizer>(model_path.str());
    const size_t n_mels = handle->model->n_mels();
    handle->pcm_samples.reserve(30 * 16000);
    handle->mel_features.resize(n_mels * 3000);
    app::ctranslate2_jni::prepare_whisper_feature_workspace(
        n_mels, handle->feature_workspace);
    return reinterpret_cast<jlong>(handle.release());
  } catch (const std::invalid_argument& error) {
    throw_java(env, "java/lang/IllegalArgumentException", error.what());
  } catch (const std::exception& error) {
    throw_java(env, "java/lang/IllegalStateException", error.what());
  }
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_CTranslate2_CTranslate2Bridge_destroy(JNIEnv*, jclass, jlong handle) {
  delete from_handle(handle);
}

extern "C" JNIEXPORT jstring JNICALL
// pcm例: short[80000]相当。PCM16をfloatへ変換して文字起こし結果を返します。
// 不正引数ではJavaのIllegalArgumentException、推論失敗ではIllegalStateExceptionを送出します。
Java_CTranslate2_CTranslate2Bridge_transcribe(
    JNIEnv* env,
    jclass,
    jlong native_handle,
    jshortArray pcm,
    jint sample_count,
    jstring language,
    jboolean translate_to_english,
    jstring initial_prompt,
    jboolean vad_enabled,
    jfloat vad_threshold,
    jint beam_size,
    jint max_length) {
  try {
    auto* handle = from_handle(native_handle);
    if (handle == nullptr || handle->model == nullptr)
      throw std::invalid_argument("native handle is null");
    if (pcm == nullptr)
      throw std::invalid_argument("samples must not be null");
    const jsize array_length = env->GetArrayLength(pcm);
    if (sample_count < 0 || sample_count > array_length)
      throw std::invalid_argument("sampleCount is outside samples");
    std::lock_guard<std::mutex> inference_lock(handle->inference_mutex);

    app::native_audio::pcm16_to_float_vector(
        env, pcm, handle->pcm_samples, sample_count);
    if (env->ExceptionCheck())
      return nullptr;

    const size_t n_mels = handle->model->n_mels();
    app::ctranslate2_jni::make_whisper_features(
        handle->pcm_samples,
        n_mels,
        handle->mel_features,
        handle->feature_workspace);
    ctranslate2::StorageView features(
        {1, static_cast<ctranslate2::dim_t>(n_mels), 3000},
        handle->mel_features.data());

    const JStringChars language_code(env, language);
    const JStringChars prompt_text(env, initial_prompt);
    std::string language_token;
    if (translate_to_english || language_code.str() == "auto") {
      auto language_futures = handle->model->detect_language(features);
      const auto probabilities = language_futures.at(0).get();
      if (probabilities.empty())
        throw std::runtime_error("CTranslate2 language detection returned no result");
      language_token = probabilities.front().first;
    } else {
      language_token = "<|" + language_code.str() + "|>";
    }
    std::vector<std::string> prompt;
    const auto initial_tokens = handle->tokenizer->encode(prompt_text.str(), 224);
    if (!initial_tokens.empty()) {
      prompt.emplace_back("<|startofprev|>");
      prompt.insert(prompt.end(), initial_tokens.begin(), initial_tokens.end());
    }
    prompt.emplace_back("<|startoftranscript|>");
    prompt.emplace_back(language_token);
    prompt.emplace_back(translate_to_english ? "<|translate|>" : "<|transcribe|>");
    prompt.emplace_back("<|notimestamps|>");
    std::vector<std::vector<std::string>> prompts{std::move(prompt)};
    ctranslate2::models::WhisperOptions options;
    options.beam_size = static_cast<size_t>(std::max(1, beam_size));
    options.max_length = static_cast<size_t>(std::max(1, max_length));
    options.sampling_topk = 1;
    options.return_scores = vad_enabled;
    options.return_no_speech_prob = vad_enabled;

    auto futures = handle->model->generate(features, std::move(prompts), options);
    const auto result = futures.at(0).get();
    if (result.sequences.empty())
      return env->NewStringUTF("");
    const float threshold = std::max(0.0f, std::min(1.0f, vad_threshold));
    const bool low_text_probability = result.scores.empty() || result.scores.front() < -1.0f;
    if (vad_enabled && result.no_speech_prob >= threshold && low_text_probability)
      return env->NewStringUTF("");
    const std::string text = app::ctranslate2_jni::decode_whisper_tokens(result.sequences[0]);
    return env->NewStringUTF(text.c_str());
  } catch (const std::invalid_argument& error) {
    throw_java(env, "java/lang/IllegalArgumentException", error.what());
  } catch (const std::exception& error) {
    throw_java(env, "java/lang/IllegalStateException", error.what());
  }
  return nullptr;
}
