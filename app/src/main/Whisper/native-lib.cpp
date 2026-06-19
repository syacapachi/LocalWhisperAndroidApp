#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>

#include "whisper.h"
// Java JNIに接続する文言を置き換える
#define JNI_METHOD(return_type, name) \
extern "C" JNIEXPORT return_type JNICALL Java_Whisper_WhisperBridge_##name

static whisper_context * as_context(jlong handle) {
    return reinterpret_cast<whisper_context *>(handle);
}

static whisper_state * as_state(jlong handle) {
    return reinterpret_cast<whisper_state *>(handle);
}

static whisper_vad_context * as_vad_context(jlong handle) {
    return reinterpret_cast<whisper_vad_context *>(handle);
}

static whisper_vad_segments * as_vad_segments(jlong handle) {
    return reinterpret_cast<whisper_vad_segments *>(handle);
}

static jstring to_jstring(JNIEnv * env, const char * value) {
    return env->NewStringUTF(value == nullptr ? "" : value);
}

static std::string to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return "";
    }

    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jfieldID field_id(JNIEnv * env, jobject object, const char * name, const char * signature) {
    jclass cls = env->GetObjectClass(object);
    jfieldID field = env->GetFieldID(cls, name, signature);
    env->DeleteLocalRef(cls);
    return field;
}

static jobject new_object(JNIEnv * env, const char * class_name) {
    jclass cls = env->FindClass(class_name);
    jmethodID ctor = env->GetMethodID(cls, "<init>", "()V");
    jobject object = env->NewObject(cls, ctor);
    env->DeleteLocalRef(cls);
    return object;
}

static jint get_int(JNIEnv * env, jobject object, const char * name, jint fallback) {
    if (object == nullptr) {
        return fallback;
    }
    return env->GetIntField(object, field_id(env, object, name, "I"));
}

static jlong get_long(JNIEnv * env, jobject object, const char * name, jlong fallback) {
    if (object == nullptr) {
        return fallback;
    }
    return env->GetLongField(object, field_id(env, object, name, "J"));
}

static jfloat get_float(JNIEnv * env, jobject object, const char * name, jfloat fallback) {
    if (object == nullptr) {
        return fallback;
    }
    return env->GetFloatField(object, field_id(env, object, name, "F"));
}

static bool get_bool(JNIEnv * env, jobject object, const char * name, bool fallback) {
    if (object == nullptr) {
        return fallback;
    }
    return env->GetBooleanField(object, field_id(env, object, name, "Z")) == JNI_TRUE;
}

static bool get_string(JNIEnv * env, jobject object, const char * name, std::string & result) {
    if (object == nullptr) {
        return false;
    }

    jstring value = static_cast<jstring>(
            env->GetObjectField(object, field_id(env, object, name, "Ljava/lang/String;")));
    if (value == nullptr) {
        return false;
    }

    result = to_string(env, value);
    env->DeleteLocalRef(value);
    return true;
}

static jobject get_object(JNIEnv * env, jobject object, const char * name, const char * signature) {
    if (object == nullptr) {
        return nullptr;
    }
    return env->GetObjectField(object, field_id(env, object, name, signature));
}

static std::vector<whisper_token> get_token_vector(JNIEnv * env, jobject object, const char * name) {
    std::vector<whisper_token> result;
    if (object == nullptr) {
        return result;
    }

    auto array = static_cast<jintArray>(
            env->GetObjectField(object, field_id(env, object, name, "[I")));
    if (array == nullptr) {
        return result;
    }

    jsize length = env->GetArrayLength(array);
    result.resize(static_cast<size_t>(length));

    if (length > 0) {
        std::vector<jint> temp(static_cast<size_t>(length));
        env->GetIntArrayRegion(array, 0, length, temp.data());
        for (jsize i = 0; i < length; i++) {
            result[static_cast<size_t>(i)] = static_cast<whisper_token>(temp[static_cast<size_t>(i)]);
        }
    }

    env->DeleteLocalRef(array);
    return result;
}

static std::vector<whisper_token> tokens_from_array(JNIEnv * env, jintArray array) {
    std::vector<whisper_token> result;
    if (array == nullptr) {
        return result;
    }

    jsize length = env->GetArrayLength(array);
    result.resize(static_cast<size_t>(length));

    if (length > 0) {
        std::vector<jint> temp(static_cast<size_t>(length));
        env->GetIntArrayRegion(array, 0, length, temp.data());
        for (jsize i = 0; i < length; i++) {
            result[static_cast<size_t>(i)] = static_cast<whisper_token>(temp[static_cast<size_t>(i)]);
        }
    }

    return result;
}

static void set_int(JNIEnv * env, jobject object, const char * name, jint value) {
    env->SetIntField(object, field_id(env, object, name, "I"), value);
}

static void set_long(JNIEnv * env, jobject object, const char * name, jlong value) {
    env->SetLongField(object, field_id(env, object, name, "J"), value);
}

static void set_float(JNIEnv * env, jobject object, const char * name, jfloat value) {
    env->SetFloatField(object, field_id(env, object, name, "F"), value);
}

static void set_bool(JNIEnv * env, jobject object, const char * name, bool value) {
    env->SetBooleanField(object, field_id(env, object, name, "Z"), value ? JNI_TRUE : JNI_FALSE);
}

static void set_string(JNIEnv * env, jobject object, const char * name, const char * value) {
    jfieldID field = field_id(env, object, name, "Ljava/lang/String;");
    if (value == nullptr) {
        env->SetObjectField(object, field, nullptr);
        return;
    }

    jstring string = env->NewStringUTF(value);
    env->SetObjectField(object, field, string);
    env->DeleteLocalRef(string);
}

static void set_object(JNIEnv * env, jobject object, const char * name, const char * signature, jobject value) {
    env->SetObjectField(object, field_id(env, object, name, signature), value);
}

static void set_float_array(JNIEnv * env, jobject object, const char * name, const std::vector<float> & values) {
    jfloatArray array = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (!values.empty()) {
        env->SetFloatArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    }
    env->SetObjectField(object, field_id(env, object, name, "[F"), array);
    env->DeleteLocalRef(array);
}

static whisper_context_params make_context_params(JNIEnv * env, jobject object) {
    whisper_context_params params = whisper_context_default_params();

    params.use_gpu = get_bool(env, object, "useGpu", params.use_gpu);
    params.flash_attn = get_bool(env, object, "flashAttn", params.flash_attn);
    params.gpu_device = get_int(env, object, "gpuDevice", params.gpu_device);
    params.dtw_token_timestamps = get_bool(env, object, "dtwTokenTimestamps", params.dtw_token_timestamps);
    params.dtw_aheads_preset = static_cast<whisper_alignment_heads_preset>(
            get_int(env, object, "dtwAheadsPreset", params.dtw_aheads_preset));
    params.dtw_n_top = get_int(env, object, "dtwNTop", params.dtw_n_top);
    params.dtw_mem_size = static_cast<size_t>(get_long(env, object, "dtwMemSize",
                                                       static_cast<jlong>(params.dtw_mem_size)));

    return params;
}

static jobject make_context_params_object(JNIEnv * env, const whisper_context_params & params) {
    jobject object = new_object(env, "Whisper/WhisperBridge$ContextParams");

    set_bool(env, object, "useGpu", params.use_gpu);
    set_bool(env, object, "flashAttn", params.flash_attn);
    set_int(env, object, "gpuDevice", params.gpu_device);
    set_bool(env, object, "dtwTokenTimestamps", params.dtw_token_timestamps);
    set_int(env, object, "dtwAheadsPreset", params.dtw_aheads_preset);
    set_int(env, object, "dtwNTop", params.dtw_n_top);
    set_long(env, object, "dtwMemSize", static_cast<jlong>(params.dtw_mem_size));

    return object;
}

static whisper_vad_params make_vad_params(JNIEnv * env, jobject object, whisper_vad_params fallback) {
    whisper_vad_params params = fallback;

    params.threshold = get_float(env, object, "threshold", params.threshold);
    params.min_speech_duration_ms = get_int(env, object, "minSpeechDurationMs", params.min_speech_duration_ms);
    params.min_silence_duration_ms = get_int(env, object, "minSilenceDurationMs", params.min_silence_duration_ms);
    params.max_speech_duration_s = get_float(env, object, "maxSpeechDurationS", params.max_speech_duration_s);
    params.speech_pad_ms = get_int(env, object, "speechPadMs", params.speech_pad_ms);
    params.samples_overlap = get_float(env, object, "samplesOverlap", params.samples_overlap);

    return params;
}

static jobject make_vad_params_object(JNIEnv * env, const whisper_vad_params & params) {
    jobject object = new_object(env, "Whisper/WhisperBridge$VadParams");

    set_float(env, object, "threshold", params.threshold);
    set_int(env, object, "minSpeechDurationMs", params.min_speech_duration_ms);
    set_int(env, object, "minSilenceDurationMs", params.min_silence_duration_ms);
    set_float(env, object, "maxSpeechDurationS", params.max_speech_duration_s);
    set_int(env, object, "speechPadMs", params.speech_pad_ms);
    set_float(env, object, "samplesOverlap", params.samples_overlap);

    return object;
}

static whisper_vad_context_params make_vad_context_params(JNIEnv * env, jobject object) {
    whisper_vad_context_params params = whisper_vad_default_context_params();

    params.n_threads = get_int(env, object, "nThreads", params.n_threads);
    params.use_gpu = get_bool(env, object, "useGpu", params.use_gpu);
    params.gpu_device = get_int(env, object, "gpuDevice", params.gpu_device);

    return params;
}

static jobject make_vad_context_params_object(JNIEnv * env, const whisper_vad_context_params & params) {
    jobject object = new_object(env, "Whisper/WhisperBridge$VadContextParams");

    set_int(env, object, "nThreads", params.n_threads);
    set_bool(env, object, "useGpu", params.use_gpu);
    set_int(env, object, "gpuDevice", params.gpu_device);

    return object;
}

struct FullParamsHolder {
    whisper_full_params params;
    std::string suppress_regex;
    std::string initial_prompt;
    std::string language;
    std::string vad_model_path;
    std::vector<whisper_token> prompt_tokens;
};

static FullParamsHolder make_full_params(JNIEnv * env, jobject object) {
    const int strategy = get_int(env, object, "strategy", WHISPER_SAMPLING_GREEDY);
    FullParamsHolder holder {
            whisper_full_default_params(static_cast<whisper_sampling_strategy>(strategy)),
            {},
            {},
            {},
            {},
            {}
    };

    whisper_full_params & params = holder.params;
    if (object == nullptr) {
        return holder;
    }

    params.strategy = static_cast<whisper_sampling_strategy>(strategy);
    params.n_threads = get_int(env, object, "nThreads", params.n_threads);
    params.n_max_text_ctx = get_int(env, object, "nMaxTextCtx", params.n_max_text_ctx);
    params.offset_ms = get_int(env, object, "offsetMs", params.offset_ms);
    params.duration_ms = get_int(env, object, "durationMs", params.duration_ms);
    params.translate = get_bool(env, object, "translate", params.translate);
    params.no_context = get_bool(env, object, "noContext", params.no_context);
    params.no_timestamps = get_bool(env, object, "noTimestamps", params.no_timestamps);
    params.single_segment = get_bool(env, object, "singleSegment", params.single_segment);
    params.print_special = get_bool(env, object, "printSpecial", params.print_special);
    params.print_progress = get_bool(env, object, "printProgress", params.print_progress);
    params.print_realtime = get_bool(env, object, "printRealtime", params.print_realtime);
    params.print_timestamps = get_bool(env, object, "printTimestamps", params.print_timestamps);
    params.token_timestamps = get_bool(env, object, "tokenTimestamps", params.token_timestamps);
    params.thold_pt = get_float(env, object, "tholdPt", params.thold_pt);
    params.thold_ptsum = get_float(env, object, "tholdPtsum", params.thold_ptsum);
    params.max_len = get_int(env, object, "maxLen", params.max_len);
    params.split_on_word = get_bool(env, object, "splitOnWord", params.split_on_word);
    params.max_tokens = get_int(env, object, "maxTokens", params.max_tokens);
    params.debug_mode = get_bool(env, object, "debugMode", params.debug_mode);
    params.audio_ctx = get_int(env, object, "audioCtx", params.audio_ctx);
    params.tdrz_enable = get_bool(env, object, "tdrzEnable", params.tdrz_enable);

    if (get_string(env, object, "suppressRegex", holder.suppress_regex)) {
        params.suppress_regex = holder.suppress_regex.c_str();
    }
    if (get_string(env, object, "initialPrompt", holder.initial_prompt)) {
        params.initial_prompt = holder.initial_prompt.c_str();
    }

    params.carry_initial_prompt = get_bool(env, object, "carryInitialPrompt", params.carry_initial_prompt);
    holder.prompt_tokens = get_token_vector(env, object, "promptTokens");
    if (!holder.prompt_tokens.empty()) {
        params.prompt_tokens = holder.prompt_tokens.data();
        params.prompt_n_tokens = static_cast<int>(holder.prompt_tokens.size());
    }

    if (get_string(env, object, "language", holder.language)) {
        params.language = holder.language.c_str();
    }
    params.detect_language = get_bool(env, object, "detectLanguage", params.detect_language);
    params.suppress_blank = get_bool(env, object, "suppressBlank", params.suppress_blank);
    params.suppress_nst = get_bool(env, object, "suppressNst", params.suppress_nst);
    params.temperature = get_float(env, object, "temperature", params.temperature);
    params.max_initial_ts = get_float(env, object, "maxInitialTs", params.max_initial_ts);
    params.length_penalty = get_float(env, object, "lengthPenalty", params.length_penalty);
    params.temperature_inc = get_float(env, object, "temperatureInc", params.temperature_inc);
    params.entropy_thold = get_float(env, object, "entropyThold", params.entropy_thold);
    params.logprob_thold = get_float(env, object, "logprobThold", params.logprob_thold);
    params.no_speech_thold = get_float(env, object, "noSpeechThold", params.no_speech_thold);
    params.greedy.best_of = get_int(env, object, "greedyBestOf", params.greedy.best_of);
    params.beam_search.beam_size = get_int(env, object, "beamSize", params.beam_search.beam_size);
    params.beam_search.patience = get_float(env, object, "beamPatience", params.beam_search.patience);
    params.vad = get_bool(env, object, "vad", params.vad);

    if (get_string(env, object, "vadModelPath", holder.vad_model_path)) {
        params.vad_model_path = holder.vad_model_path.c_str();
    }

    jobject vad_params = get_object(env, object, "vadParams", "LWhisper/WhisperBridge$VadParams;");
    if (vad_params != nullptr) {
        params.vad_params = make_vad_params(env, vad_params, params.vad_params);
        env->DeleteLocalRef(vad_params);
    }

    return holder;
}

static jobject make_full_params_object(JNIEnv * env, whisper_full_params params) {
    jobject object = new_object(env, "Whisper/WhisperBridge$FullParams");

    set_int(env, object, "strategy", params.strategy);
    set_int(env, object, "nThreads", params.n_threads);
    set_int(env, object, "nMaxTextCtx", params.n_max_text_ctx);
    set_int(env, object, "offsetMs", params.offset_ms);
    set_int(env, object, "durationMs", params.duration_ms);
    set_bool(env, object, "translate", params.translate);
    set_bool(env, object, "noContext", params.no_context);
    set_bool(env, object, "noTimestamps", params.no_timestamps);
    set_bool(env, object, "singleSegment", params.single_segment);
    set_bool(env, object, "printSpecial", params.print_special);
    set_bool(env, object, "printProgress", params.print_progress);
    set_bool(env, object, "printRealtime", params.print_realtime);
    set_bool(env, object, "printTimestamps", params.print_timestamps);
    set_bool(env, object, "tokenTimestamps", params.token_timestamps);
    set_float(env, object, "tholdPt", params.thold_pt);
    set_float(env, object, "tholdPtsum", params.thold_ptsum);
    set_int(env, object, "maxLen", params.max_len);
    set_bool(env, object, "splitOnWord", params.split_on_word);
    set_int(env, object, "maxTokens", params.max_tokens);
    set_bool(env, object, "debugMode", params.debug_mode);
    set_int(env, object, "audioCtx", params.audio_ctx);
    set_bool(env, object, "tdrzEnable", params.tdrz_enable);
    set_string(env, object, "suppressRegex", params.suppress_regex);
    set_string(env, object, "initialPrompt", params.initial_prompt);
    set_bool(env, object, "carryInitialPrompt", params.carry_initial_prompt);
    set_string(env, object, "language", params.language);
    set_bool(env, object, "detectLanguage", params.detect_language);
    set_bool(env, object, "suppressBlank", params.suppress_blank);
    set_bool(env, object, "suppressNst", params.suppress_nst);
    set_float(env, object, "temperature", params.temperature);
    set_float(env, object, "maxInitialTs", params.max_initial_ts);
    set_float(env, object, "lengthPenalty", params.length_penalty);
    set_float(env, object, "temperatureInc", params.temperature_inc);
    set_float(env, object, "entropyThold", params.entropy_thold);
    set_float(env, object, "logprobThold", params.logprob_thold);
    set_float(env, object, "noSpeechThold", params.no_speech_thold);
    set_int(env, object, "greedyBestOf", params.greedy.best_of);
    set_int(env, object, "beamSize", params.beam_search.beam_size);
    set_float(env, object, "beamPatience", params.beam_search.patience);
    set_bool(env, object, "vad", params.vad);
    set_string(env, object, "vadModelPath", params.vad_model_path);

    jobject vad_params = make_vad_params_object(env, params.vad_params);
    set_object(env, object, "vadParams", "LWhisper/WhisperBridge$VadParams;", vad_params);
    env->DeleteLocalRef(vad_params);

    return object;
}

static jobject make_token_data_object(JNIEnv * env, const whisper_token_data & data) {
    jobject object = new_object(env, "Whisper/WhisperBridge$TokenData");

    set_int(env, object, "id", data.id);
    set_int(env, object, "tid", data.tid);
    set_float(env, object, "p", data.p);
    set_float(env, object, "plog", data.plog);
    set_float(env, object, "pt", data.pt);
    set_float(env, object, "ptsum", data.ptsum);
    set_long(env, object, "t0", data.t0);
    set_long(env, object, "t1", data.t1);
    set_long(env, object, "tDtw", data.t_dtw);
    set_float(env, object, "vlen", data.vlen);

    return object;
}

static jobject make_timings_object(JNIEnv * env, const whisper_timings & timings) {
    jobject object = new_object(env, "Whisper/WhisperBridge$Timings");

    set_float(env, object, "sampleMs", timings.sample_ms);
    set_float(env, object, "encodeMs", timings.encode_ms);
    set_float(env, object, "decodeMs", timings.decode_ms);
    set_float(env, object, "batchdMs", timings.batchd_ms);
    set_float(env, object, "promptMs", timings.prompt_ms);

    return object;
}

static jobject make_lang_detection_object(JNIEnv * env, int language_id, const std::vector<float> & probabilities) {
    jobject object = new_object(env, "Whisper/WhisperBridge$LangDetection");
    set_int(env, object, "languageId", language_id);
    set_float_array(env, object, "probabilities", probabilities);
    return object;
}

static jintArray int_array(JNIEnv * env, const std::vector<whisper_token> & values, int count) {
    count = std::max(0, std::min(count, static_cast<int>(values.size())));
    jintArray array = env->NewIntArray(count);

    if (count > 0) {
        std::vector<jint> temp(static_cast<size_t>(count));
        for (int i = 0; i < count; i++) {
            temp[static_cast<size_t>(i)] = values[static_cast<size_t>(i)];
        }
        env->SetIntArrayRegion(array, 0, count, temp.data());
    }

    return array;
}

static jfloatArray float_array(JNIEnv * env, const float * values, int count) {
    count = std::max(0, count);
    jfloatArray array = env->NewFloatArray(count);

    if (count > 0 && values != nullptr) {
        env->SetFloatArrayRegion(array, 0, count, values);
    }

    return array;
}

template <typename Fn>
static int with_float_array(JNIEnv * env, jfloatArray array, Fn fn) {
    if (array == nullptr) {
        return -1;
    }

    jsize length = env->GetArrayLength(array);
    jfloat * data = env->GetFloatArrayElements(array, nullptr);
    int result = data == nullptr ? -1 : fn(data, static_cast<int>(length));
    env->ReleaseFloatArrayElements(array, data, JNI_ABORT);
    return result;
}

JNI_METHOD(jstring, version)(JNIEnv * env, jclass) {
    return to_jstring(env, whisper_version());
}

JNI_METHOD(jstring, systemInfo)(JNIEnv * env, jclass) {
    return to_jstring(env, whisper_print_system_info());
}

JNI_METHOD(jobject, defaultContextParams)(JNIEnv * env, jclass) {
    return make_context_params_object(env, whisper_context_default_params());
}

JNI_METHOD(jobject, defaultFullParams)(JNIEnv * env, jclass, jint strategy) {
    return make_full_params_object(env, whisper_full_default_params(
            static_cast<whisper_sampling_strategy>(strategy)));
}

JNI_METHOD(jobject, defaultVadParams)(JNIEnv * env, jclass) {
    return make_vad_params_object(env, whisper_vad_default_params());
}

JNI_METHOD(jobject, defaultVadContextParams)(JNIEnv * env, jclass) {
    return make_vad_context_params_object(env, whisper_vad_default_context_params());
}

JNI_METHOD(jlong, initFromFile)(JNIEnv * env, jclass, jstring model_path, jobject params_object) {
    std::string path = to_string(env, model_path);
    whisper_context_params params = make_context_params(env, params_object);
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params(path.c_str(), params));
}

JNI_METHOD(jlong, initFromFileNoState)(JNIEnv * env, jclass, jstring model_path, jobject params_object) {
    std::string path = to_string(env, model_path);
    whisper_context_params params = make_context_params(env, params_object);
    return reinterpret_cast<jlong>(whisper_init_from_file_with_params_no_state(path.c_str(), params));
}

JNI_METHOD(jlong, initFromBuffer)(JNIEnv * env, jclass, jbyteArray model_buffer, jobject params_object) {
    if (model_buffer == nullptr) {
        return 0;
    }

    jsize length = env->GetArrayLength(model_buffer);
    jbyte * data = env->GetByteArrayElements(model_buffer, nullptr);
    whisper_context_params params = make_context_params(env, params_object);
    whisper_context * context = data == nullptr
            ? nullptr
            : whisper_init_from_buffer_with_params(data, static_cast<size_t>(length), params);
    env->ReleaseByteArrayElements(model_buffer, data, JNI_ABORT);
    return reinterpret_cast<jlong>(context);
}

JNI_METHOD(jlong, initFromBufferNoState)(JNIEnv * env, jclass, jbyteArray model_buffer, jobject params_object) {
    if (model_buffer == nullptr) {
        return 0;
    }

    jsize length = env->GetArrayLength(model_buffer);
    jbyte * data = env->GetByteArrayElements(model_buffer, nullptr);
    whisper_context_params params = make_context_params(env, params_object);
    whisper_context * context = data == nullptr
            ? nullptr
            : whisper_init_from_buffer_with_params_no_state(data, static_cast<size_t>(length), params);
    env->ReleaseByteArrayElements(model_buffer, data, JNI_ABORT);
    return reinterpret_cast<jlong>(context);
}

JNI_METHOD(jlong, initState)(JNIEnv *, jclass, jlong context) {
    return reinterpret_cast<jlong>(whisper_init_state(as_context(context)));
}

JNI_METHOD(void, freeContext)(JNIEnv *, jclass, jlong context) {
    whisper_free(as_context(context));
}

JNI_METHOD(void, freeState)(JNIEnv *, jclass, jlong state) {
    whisper_free_state(as_state(state));
}

JNI_METHOD(jint, full)(JNIEnv * env, jclass, jlong context, jobject params_object, jfloatArray pcm_data) {
    whisper_context * ctx = as_context(context);
    FullParamsHolder params = make_full_params(env, params_object);
    return with_float_array(env, pcm_data, [&](const float * samples, int count) {
        return whisper_full(ctx, params.params, samples, count);
    });
}

JNI_METHOD(jint, fullWithState)(JNIEnv * env, jclass, jlong context, jlong state, jobject params_object, jfloatArray pcm_data) {
    whisper_context * ctx = as_context(context);
    whisper_state * st = as_state(state);
    FullParamsHolder params = make_full_params(env, params_object);
    return with_float_array(env, pcm_data, [&](const float * samples, int count) {
        return whisper_full_with_state(ctx, st, params.params, samples, count);
    });
}

JNI_METHOD(jint, fullParallel)(JNIEnv * env, jclass, jlong context, jobject params_object, jfloatArray pcm_data, jint processors) {
    whisper_context * ctx = as_context(context);
    FullParamsHolder params = make_full_params(env, params_object);
    return with_float_array(env, pcm_data, [&](const float * samples, int count) {
        return whisper_full_parallel(ctx, params.params, samples, count, processors);
    });
}

JNI_METHOD(jint, pcmToMel)(JNIEnv * env, jclass, jlong context, jfloatArray samples, jint threads) {
    return with_float_array(env, samples, [&](const float * data, int count) {
        return whisper_pcm_to_mel(as_context(context), data, count, threads);
    });
}

JNI_METHOD(jint, pcmToMelWithState)(JNIEnv * env, jclass, jlong context, jlong state, jfloatArray samples, jint threads) {
    return with_float_array(env, samples, [&](const float * data, int count) {
        return whisper_pcm_to_mel_with_state(as_context(context), as_state(state), data, count, threads);
    });
}

JNI_METHOD(jint, setMel)(JNIEnv * env, jclass, jlong context, jfloatArray mel_data, jint mel_length, jint mel_bands) {
    return with_float_array(env, mel_data, [&](const float * data, int) {
        return whisper_set_mel(as_context(context), data, mel_length, mel_bands);
    });
}

JNI_METHOD(jint, setMelWithState)(JNIEnv * env, jclass, jlong context, jlong state, jfloatArray mel_data, jint mel_length, jint mel_bands) {
    return with_float_array(env, mel_data, [&](const float * data, int) {
        return whisper_set_mel_with_state(as_context(context), as_state(state), data, mel_length, mel_bands);
    });
}

JNI_METHOD(jint, encode)(JNIEnv *, jclass, jlong context, jint offset, jint threads) {
    return whisper_encode(as_context(context), offset, threads);
}

JNI_METHOD(jint, encodeWithState)(JNIEnv *, jclass, jlong context, jlong state, jint offset, jint threads) {
    return whisper_encode_with_state(as_context(context), as_state(state), offset, threads);
}

JNI_METHOD(jint, decode)(JNIEnv * env, jclass, jlong context, jintArray token_array, jint past_tokens, jint threads) {
    std::vector<whisper_token> tokens = tokens_from_array(env, token_array);
    return whisper_decode(as_context(context), tokens.data(), static_cast<int>(tokens.size()), past_tokens, threads);
}

JNI_METHOD(jint, decodeWithState)(JNIEnv * env, jclass, jlong context, jlong state, jintArray token_array, jint past_tokens, jint threads) {
    std::vector<whisper_token> tokens = tokens_from_array(env, token_array);
    return whisper_decode_with_state(as_context(context), as_state(state), tokens.data(),
                                     static_cast<int>(tokens.size()), past_tokens, threads);
}

JNI_METHOD(jintArray, tokenize)(JNIEnv * env, jclass, jlong context, jstring text, jint max_tokens) {
    whisper_context * ctx = as_context(context);
    std::string value = to_string(env, text);

    int capacity = max_tokens;
    if (capacity <= 0) {
        capacity = std::max(1, std::abs(whisper_token_count(ctx, value.c_str())));
    }

    std::vector<whisper_token> tokens(static_cast<size_t>(capacity));
    int count = whisper_tokenize(ctx, value.c_str(), tokens.data(), capacity);
    if (count < 0) {
        capacity = -count;
        tokens.resize(static_cast<size_t>(capacity));
        count = whisper_tokenize(ctx, value.c_str(), tokens.data(), capacity);
    }

    return int_array(env, tokens, count);
}

JNI_METHOD(jint, tokenCount)(JNIEnv * env, jclass, jlong context, jstring text) {
    std::string value = to_string(env, text);
    return whisper_token_count(as_context(context), value.c_str());
}

JNI_METHOD(jstring, tokenToString)(JNIEnv * env, jclass, jlong context, jint token) {
    return to_jstring(env, whisper_token_to_str(as_context(context), token));
}

JNI_METHOD(jint, tokenEot)(JNIEnv *, jclass, jlong context) { return whisper_token_eot(as_context(context)); }
JNI_METHOD(jint, tokenSot)(JNIEnv *, jclass, jlong context) { return whisper_token_sot(as_context(context)); }
JNI_METHOD(jint, tokenSolm)(JNIEnv *, jclass, jlong context) { return whisper_token_solm(as_context(context)); }
JNI_METHOD(jint, tokenPrev)(JNIEnv *, jclass, jlong context) { return whisper_token_prev(as_context(context)); }
JNI_METHOD(jint, tokenNosp)(JNIEnv *, jclass, jlong context) { return whisper_token_nosp(as_context(context)); }
JNI_METHOD(jint, tokenNot)(JNIEnv *, jclass, jlong context) { return whisper_token_not(as_context(context)); }
JNI_METHOD(jint, tokenBeg)(JNIEnv *, jclass, jlong context) { return whisper_token_beg(as_context(context)); }
JNI_METHOD(jint, tokenLang)(JNIEnv *, jclass, jlong context, jint language_id) { return whisper_token_lang(as_context(context), language_id); }
JNI_METHOD(jint, tokenTranslate)(JNIEnv *, jclass, jlong context) { return whisper_token_translate(as_context(context)); }
JNI_METHOD(jint, tokenTranscribe)(JNIEnv *, jclass, jlong context) { return whisper_token_transcribe(as_context(context)); }

JNI_METHOD(jint, langMaxId)(JNIEnv *, jclass) {
    return whisper_lang_max_id();
}

JNI_METHOD(jint, langId)(JNIEnv * env, jclass, jstring language) {
    std::string value = to_string(env, language);
    return whisper_lang_id(value.c_str());
}

JNI_METHOD(jstring, langStr)(JNIEnv * env, jclass, jint language_id) {
    return to_jstring(env, whisper_lang_str(language_id));
}

JNI_METHOD(jstring, langStrFull)(JNIEnv * env, jclass, jint language_id) {
    return to_jstring(env, whisper_lang_str_full(language_id));
}

JNI_METHOD(jobject, langAutoDetect)(JNIEnv * env, jclass, jlong context, jint offset_ms, jint threads) {
    const int count = whisper_lang_max_id() + 1;
    std::vector<float> probabilities(static_cast<size_t>(count));
    int language_id = whisper_lang_auto_detect(as_context(context), offset_ms, threads, probabilities.data());
    return make_lang_detection_object(env, language_id, probabilities);
}

JNI_METHOD(jobject, langAutoDetectWithState)(JNIEnv * env, jclass, jlong context, jlong state, jint offset_ms, jint threads) {
    const int count = whisper_lang_max_id() + 1;
    std::vector<float> probabilities(static_cast<size_t>(count));
    int language_id = whisper_lang_auto_detect_with_state(
            as_context(context), as_state(state), offset_ms, threads, probabilities.data());
    return make_lang_detection_object(env, language_id, probabilities);
}

JNI_METHOD(jint, nLen)(JNIEnv *, jclass, jlong context) { return whisper_n_len(as_context(context)); }
JNI_METHOD(jint, nLenFromState)(JNIEnv *, jclass, jlong state) { return whisper_n_len_from_state(as_state(state)); }
JNI_METHOD(jint, nVocab)(JNIEnv *, jclass, jlong context) { return whisper_n_vocab(as_context(context)); }
JNI_METHOD(jint, nTextCtx)(JNIEnv *, jclass, jlong context) { return whisper_n_text_ctx(as_context(context)); }
JNI_METHOD(jint, nAudioCtx)(JNIEnv *, jclass, jlong context) { return whisper_n_audio_ctx(as_context(context)); }
JNI_METHOD(jboolean, isMultilingual)(JNIEnv *, jclass, jlong context) { return whisper_is_multilingual(as_context(context)) ? JNI_TRUE : JNI_FALSE; }
JNI_METHOD(jint, modelNVocab)(JNIEnv *, jclass, jlong context) { return whisper_model_n_vocab(as_context(context)); }
JNI_METHOD(jint, modelNAudioCtx)(JNIEnv *, jclass, jlong context) { return whisper_model_n_audio_ctx(as_context(context)); }
JNI_METHOD(jint, modelNAudioState)(JNIEnv *, jclass, jlong context) { return whisper_model_n_audio_state(as_context(context)); }
JNI_METHOD(jint, modelNAudioHead)(JNIEnv *, jclass, jlong context) { return whisper_model_n_audio_head(as_context(context)); }
JNI_METHOD(jint, modelNAudioLayer)(JNIEnv *, jclass, jlong context) { return whisper_model_n_audio_layer(as_context(context)); }
JNI_METHOD(jint, modelNTextCtx)(JNIEnv *, jclass, jlong context) { return whisper_model_n_text_ctx(as_context(context)); }
JNI_METHOD(jint, modelNTextState)(JNIEnv *, jclass, jlong context) { return whisper_model_n_text_state(as_context(context)); }
JNI_METHOD(jint, modelNTextHead)(JNIEnv *, jclass, jlong context) { return whisper_model_n_text_head(as_context(context)); }
JNI_METHOD(jint, modelNTextLayer)(JNIEnv *, jclass, jlong context) { return whisper_model_n_text_layer(as_context(context)); }
JNI_METHOD(jint, modelNMels)(JNIEnv *, jclass, jlong context) { return whisper_model_n_mels(as_context(context)); }
JNI_METHOD(jint, modelFType)(JNIEnv *, jclass, jlong context) { return whisper_model_ftype(as_context(context)); }
JNI_METHOD(jint, modelType)(JNIEnv *, jclass, jlong context) { return whisper_model_type(as_context(context)); }

JNI_METHOD(jstring, modelTypeReadable)(JNIEnv * env, jclass, jlong context) {
    return to_jstring(env, whisper_model_type_readable(as_context(context)));
}

JNI_METHOD(jfloatArray, logits)(JNIEnv * env, jclass, jlong context) {
    whisper_context * ctx = as_context(context);
    return float_array(env, whisper_get_logits(ctx), whisper_n_vocab(ctx));
}

JNI_METHOD(jfloatArray, logitsFromState)(JNIEnv * env, jclass, jlong state, jint vocabulary_size) {
    return float_array(env, whisper_get_logits_from_state(as_state(state)), vocabulary_size);
}

JNI_METHOD(jint, fullNSegments)(JNIEnv *, jclass, jlong context) { return whisper_full_n_segments(as_context(context)); }
JNI_METHOD(jint, fullNSegmentsFromState)(JNIEnv *, jclass, jlong state) { return whisper_full_n_segments_from_state(as_state(state)); }
JNI_METHOD(jint, fullLangId)(JNIEnv *, jclass, jlong context) { return whisper_full_lang_id(as_context(context)); }
JNI_METHOD(jint, fullLangIdFromState)(JNIEnv *, jclass, jlong state) { return whisper_full_lang_id_from_state(as_state(state)); }
JNI_METHOD(jlong, fullSegmentT0)(JNIEnv *, jclass, jlong context, jint segment) { return whisper_full_get_segment_t0(as_context(context), segment); }
JNI_METHOD(jlong, fullSegmentT0FromState)(JNIEnv *, jclass, jlong state, jint segment) { return whisper_full_get_segment_t0_from_state(as_state(state), segment); }
JNI_METHOD(jlong, fullSegmentT1)(JNIEnv *, jclass, jlong context, jint segment) { return whisper_full_get_segment_t1(as_context(context), segment); }
JNI_METHOD(jlong, fullSegmentT1FromState)(JNIEnv *, jclass, jlong state, jint segment) { return whisper_full_get_segment_t1_from_state(as_state(state), segment); }
JNI_METHOD(jboolean, fullSegmentSpeakerTurnNext)(JNIEnv *, jclass, jlong context, jint segment) { return whisper_full_get_segment_speaker_turn_next(as_context(context), segment) ? JNI_TRUE : JNI_FALSE; }
JNI_METHOD(jboolean, fullSegmentSpeakerTurnNextFromState)(JNIEnv *, jclass, jlong state, jint segment) { return whisper_full_get_segment_speaker_turn_next_from_state(as_state(state), segment) ? JNI_TRUE : JNI_FALSE; }

JNI_METHOD(jstring, fullSegmentText)(JNIEnv * env, jclass, jlong context, jint segment) {
    return to_jstring(env, whisper_full_get_segment_text(as_context(context), segment));
}

JNI_METHOD(jstring, fullSegmentTextFromState)(JNIEnv * env, jclass, jlong state, jint segment) {
    return to_jstring(env, whisper_full_get_segment_text_from_state(as_state(state), segment));
}

JNI_METHOD(jint, fullNTokens)(JNIEnv *, jclass, jlong context, jint segment) { return whisper_full_n_tokens(as_context(context), segment); }
JNI_METHOD(jint, fullNTokensFromState)(JNIEnv *, jclass, jlong state, jint segment) { return whisper_full_n_tokens_from_state(as_state(state), segment); }

JNI_METHOD(jstring, fullTokenText)(JNIEnv * env, jclass, jlong context, jint segment, jint token) {
    return to_jstring(env, whisper_full_get_token_text(as_context(context), segment, token));
}

JNI_METHOD(jstring, fullTokenTextFromState)(JNIEnv * env, jclass, jlong context, jlong state, jint segment, jint token) {
    return to_jstring(env, whisper_full_get_token_text_from_state(as_context(context), as_state(state), segment, token));
}

JNI_METHOD(jint, fullTokenId)(JNIEnv *, jclass, jlong context, jint segment, jint token) {
    return whisper_full_get_token_id(as_context(context), segment, token);
}

JNI_METHOD(jint, fullTokenIdFromState)(JNIEnv *, jclass, jlong state, jint segment, jint token) {
    return whisper_full_get_token_id_from_state(as_state(state), segment, token);
}

JNI_METHOD(jobject, fullTokenData)(JNIEnv * env, jclass, jlong context, jint segment, jint token) {
    return make_token_data_object(env, whisper_full_get_token_data(as_context(context), segment, token));
}

JNI_METHOD(jobject, fullTokenDataFromState)(JNIEnv * env, jclass, jlong state, jint segment, jint token) {
    return make_token_data_object(env, whisper_full_get_token_data_from_state(as_state(state), segment, token));
}

JNI_METHOD(jfloat, fullTokenProbability)(JNIEnv *, jclass, jlong context, jint segment, jint token) {
    return whisper_full_get_token_p(as_context(context), segment, token);
}

JNI_METHOD(jfloat, fullTokenProbabilityFromState)(JNIEnv *, jclass, jlong state, jint segment, jint token) {
    return whisper_full_get_token_p_from_state(as_state(state), segment, token);
}

JNI_METHOD(jfloat, fullSegmentNoSpeechProbability)(JNIEnv *, jclass, jlong context, jint segment) {
    return whisper_full_get_segment_no_speech_prob(as_context(context), segment);
}

JNI_METHOD(jfloat, fullSegmentNoSpeechProbabilityFromState)(JNIEnv *, jclass, jlong state, jint segment) {
    return whisper_full_get_segment_no_speech_prob_from_state(as_state(state), segment);
}

JNI_METHOD(jobject, timings)(JNIEnv * env, jclass, jlong context) {
    whisper_timings * timings = whisper_get_timings(as_context(context));
    if (timings == nullptr) {
        return nullptr;
    }
    return make_timings_object(env, *timings);
}

JNI_METHOD(void, printTimings)(JNIEnv *, jclass, jlong context) {
    whisper_print_timings(as_context(context));
}

JNI_METHOD(void, resetTimings)(JNIEnv *, jclass, jlong context) {
    whisper_reset_timings(as_context(context));
}

JNI_METHOD(jlong, vadInitFromFile)(JNIEnv * env, jclass, jstring model_path, jobject params_object) {
    std::string path = to_string(env, model_path);
    whisper_vad_context_params params = make_vad_context_params(env, params_object);
    return reinterpret_cast<jlong>(whisper_vad_init_from_file_with_params(path.c_str(), params));
}

JNI_METHOD(jboolean, vadDetectSpeech)(JNIEnv * env, jclass, jlong vad_context, jfloatArray samples) {
    int result = with_float_array(env, samples, [&](const float * data, int count) {
        return whisper_vad_detect_speech(as_vad_context(vad_context), data, count) ? 1 : 0;
    });
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}

JNI_METHOD(jboolean, vadDetectSpeechNoReset)(JNIEnv * env, jclass, jlong vad_context, jfloatArray samples) {
    int result = with_float_array(env, samples, [&](const float * data, int count) {
        return whisper_vad_detect_speech_no_reset(as_vad_context(vad_context), data, count) ? 1 : 0;
    });
    return result == 1 ? JNI_TRUE : JNI_FALSE;
}

JNI_METHOD(void, vadResetState)(JNIEnv *, jclass, jlong vad_context) {
    whisper_vad_reset_state(as_vad_context(vad_context));
}

JNI_METHOD(jfloatArray, vadProbabilities)(JNIEnv * env, jclass, jlong vad_context) {
    whisper_vad_context * context = as_vad_context(vad_context);
    return float_array(env, whisper_vad_probs(context), whisper_vad_n_probs(context));
}

JNI_METHOD(jlong, vadSegmentsFromProbabilities)(JNIEnv * env, jclass, jlong vad_context, jobject params_object) {
    whisper_vad_params params = make_vad_params(env, params_object, whisper_vad_default_params());
    return reinterpret_cast<jlong>(whisper_vad_segments_from_probs(as_vad_context(vad_context), params));
}

JNI_METHOD(jlong, vadSegmentsFromSamples)(JNIEnv * env, jclass, jlong vad_context, jobject params_object, jfloatArray samples) {
    whisper_vad_params params = make_vad_params(env, params_object, whisper_vad_default_params());
    jlong result = 0;
    with_float_array(env, samples, [&](const float * data, int count) {
        result = reinterpret_cast<jlong>(
                whisper_vad_segments_from_samples(as_vad_context(vad_context), params, data, count));
        return 0;
    });
    return result;
}

JNI_METHOD(jint, vadSegmentCount)(JNIEnv *, jclass, jlong vad_segments) {
    return whisper_vad_segments_n_segments(as_vad_segments(vad_segments));
}

JNI_METHOD(jfloat, vadSegmentT0)(JNIEnv *, jclass, jlong vad_segments, jint segment) {
    return whisper_vad_segments_get_segment_t0(as_vad_segments(vad_segments), segment);
}

JNI_METHOD(jfloat, vadSegmentT1)(JNIEnv *, jclass, jlong vad_segments, jint segment) {
    return whisper_vad_segments_get_segment_t1(as_vad_segments(vad_segments), segment);
}

JNI_METHOD(void, vadFreeSegments)(JNIEnv *, jclass, jlong vad_segments) {
    whisper_vad_free_segments(as_vad_segments(vad_segments));
}

JNI_METHOD(void, vadFree)(JNIEnv *, jclass, jlong vad_context) {
    whisper_vad_free(as_vad_context(vad_context));
}

JNI_METHOD(jint, benchMemcpy)(JNIEnv *, jclass, jint threads) {
    return whisper_bench_memcpy(threads);
}

JNI_METHOD(jstring, benchMemcpyString)(JNIEnv * env, jclass, jint threads) {
    return to_jstring(env, whisper_bench_memcpy_str(threads));
}

JNI_METHOD(jint, benchGgmlMulMat)(JNIEnv *, jclass, jint threads) {
    return whisper_bench_ggml_mul_mat(threads);
}

JNI_METHOD(jstring, benchGgmlMulMatString)(JNIEnv * env, jclass, jint threads) {
    return to_jstring(env, whisper_bench_ggml_mul_mat_str(threads));
}
