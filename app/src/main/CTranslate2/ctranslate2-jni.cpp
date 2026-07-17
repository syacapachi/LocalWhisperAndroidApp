#include <jni.h>

#include <algorithm>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include <ctranslate2/models/whisper.h>
#include <ctranslate2/storage_view.h>
#include <ctranslate2/types.h>

#include "whisper-feature-extractor.h"
#include "whisper-token-decoder.h"

namespace {

struct WhisperHandle {
  std::unique_ptr<ctranslate2::models::Whisper> model;
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
Java_CTranslate2_CTranslate2Bridge_transcribe(
    JNIEnv* env,
    jclass,
    jlong native_handle,
    jfloatArray pcm,
    jstring language,
    jint beam_size,
    jint max_length) {
  try {
    auto* handle = from_handle(native_handle);
    if (handle == nullptr || handle->model == nullptr)
      throw std::invalid_argument("native handle is null");
    if (pcm == nullptr)
      throw std::invalid_argument("samples must not be null");

    const jsize sample_count = env->GetArrayLength(pcm);
    std::vector<float> samples(static_cast<size_t>(sample_count));
    env->GetFloatArrayRegion(pcm, 0, sample_count, samples.data());
    if (env->ExceptionCheck())
      return nullptr;

    const size_t n_mels = handle->model->n_mels();
    auto values = app::ctranslate2_jni::make_whisper_features(samples, n_mels);
    ctranslate2::StorageView features(
        {1, static_cast<ctranslate2::dim_t>(n_mels), 3000},
        values);

    const JStringChars language_code(env, language);
    std::string language_token;
    if (language_code.str() == "auto") {
      auto language_futures = handle->model->detect_language(features);
      const auto probabilities = language_futures.at(0).get();
      if (probabilities.empty())
        throw std::runtime_error("CTranslate2 language detection returned no result");
      language_token = probabilities.front().first;
    } else {
      language_token = "<|" + language_code.str() + "|>";
    }
    std::vector<std::vector<std::string>> prompts{{
        "<|startoftranscript|>", language_token, "<|transcribe|>", "<|notimestamps|>"}};
    ctranslate2::models::WhisperOptions options;
    options.beam_size = static_cast<size_t>(std::max(1, beam_size));
    options.max_length = static_cast<size_t>(std::max(1, max_length));
    options.sampling_topk = 1;

    auto futures = handle->model->generate(features, std::move(prompts), options);
    const auto result = futures.at(0).get();
    if (result.sequences.empty())
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
