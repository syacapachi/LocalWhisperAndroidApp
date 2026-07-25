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
#include "whisper-vad-filter.h"
#include "../NativeAudio/pcm16-float-converter.h"
#include "../NativeText/utf-converter.h"

namespace {

struct WhisperHandle {
  std::unique_ptr<ctranslate2::models::Whisper> model;
  std::unique_ptr<app::ctranslate2_jni::WhisperPromptTokenizer> tokenizer;
  std::mutex inference_mutex;
  std::vector<float> pcm_samples;
  std::vector<float> vad_samples;
  std::vector<float> mel_features;
  app::ctranslate2_jni::WhisperFeatureWorkspace feature_workspace;
  app::ctranslate2_jni::WhisperVadContextPtr vad_context;
};

class JStringUtf8 {
public:
  // value例: Javaの"こんにちは"。通常UTF-8へ変換し、JNI取得失敗時は空文字を保持します。
  // std::bad_allocは変換先メモリを確保できない場合に送出します。
  JStringUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr)
      return;
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr)
      return;
    try {
      _text = app::native_text::utf16_to_utf8_replacing_invalid(
          reinterpret_cast<const std::uint16_t*>(chars),
          static_cast<std::size_t>(length));
    } catch (...) {
      env->ReleaseStringChars(value, chars);
      throw;
    }
    env->ReleaseStringChars(value, chars);
  }

  // 引数なし。戻り値例: "こんにちは"の通常UTF-8。例外は送出しません。
  const std::string& str() const noexcept {
    return _text;
  }

 private:
  std::string _text;
};

// text例: 通常UTF-8の"こんにちは"。Java String例"こんにちは"を返します。
// 不正UTF-8はU+FFFDへ置換し、JNIがメモリを確保できない場合はnullを返して例外を保留します。
jstring new_java_string_from_utf8(JNIEnv* env, const std::string& text) {
  const std::u16string utf16 =
      app::native_text::utf8_to_utf16_replacing_invalid(text);
  static_assert(sizeof(jchar) == sizeof(char16_t));
  return env->NewString(
      reinterpret_cast<const jchar*>(utf16.data()),
      static_cast<jsize>(utf16.size()));
}

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
    jstring vad_model_path,
    jint threads) {
  try {
    const JStringUtf8 model_path(env, model_directory);
    const JStringUtf8 compute(env, compute_type);
    const JStringUtf8 vad_path(env, vad_model_path);
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
    if (!vad_path.str().empty()) {
      handle->vad_context =
          app::ctranslate2_jni::create_whisper_vad_context(vad_path.str());
    }
    const size_t n_mels = handle->model->n_mels();
    handle->pcm_samples.reserve(30 * 16000);
    handle->vad_samples.reserve(30 * 16000);
    handle->mel_features.resize(n_mels * 3000);
    app::ctranslate2_jni::prepare_whisper_feature_workspace(
        n_mels, handle->feature_workspace, static_cast<size_t>(threads));
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
    // 排他処理の開始
    std::lock_guard<std::mutex> inference_lock(handle->inference_mutex);
    // short[]をfloat[](-1~1)へ変換
    app::native_audio::pcm16_to_float_vector(
        env, pcm, handle->pcm_samples, sample_count);
    if (env->ExceptionCheck())
      return nullptr;

    const std::vector<float>* inference_samples = &handle->pcm_samples;
    if (vad_enabled) {
      if (!handle->vad_context)
        throw std::runtime_error("Silero VAD is enabled but its model is not loaded");
      // VADに通して、発話区間だけ取り出す。
      const bool has_speech = app::ctranslate2_jni::collect_whisper_vad_speech(
          handle->vad_context.get(),
          handle->pcm_samples,
          vad_threshold,
          handle->vad_samples);
      if (!has_speech)
        return new_java_string_from_utf8(env, "");
      inference_samples = &handle->vad_samples;
    }
    // 軸に対する周波数の解像度(経験的に80,128が多い)
    const size_t n_mels = handle->model->n_mels();
    // float[](-1~1)を、log_mel spectrogram(周波数軸を人間の聴覚特性に合わせて、人の感覚に合わせつつ圧縮)に変換。
    app::ctranslate2_jni::make_whisper_features(
        *inference_samples,
        n_mels,
        handle->mel_features,
        handle->feature_workspace);
    ctranslate2::StorageView features(
        {1, static_cast<ctranslate2::dim_t>(n_mels), 3000},
        handle->mel_features.data());

    const JStringUtf8 language_code(env, language);
    const JStringUtf8 prompt_text(env, initial_prompt);
    std::string language_token;
    // 自動検出モードの場合言語検出を実行
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
    options.return_scores = false;
    options.return_no_speech_prob = false;

    // 文字起こしを実行
    auto futures = handle->model->generate(features, std::move(prompts), options);
      // 結果は、複数の候補でやってくるので、先頭(最も確率が高い)を使う。
    const auto result = futures.at(0).get();
    if (result.sequences.empty())
      return new_java_string_from_utf8(env, "");
      // 結果は、複数の候補でやってくるので、先頭(最も確率が高い)を使う。
    const std::string text = app::ctranslate2_jni::decode_whisper_tokens(result.sequences[0]);
    return new_java_string_from_utf8(env, text);
  } catch (const std::invalid_argument& error) {
    throw_java(env, "java/lang/IllegalArgumentException", error.what());
  } catch (const std::exception& error) {
    throw_java(env, "java/lang/IllegalStateException", error.what());
  }
  return nullptr;
}
