#include <jni.h>
#include <string>
#include "whisper.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_Whisper_WhisperBridge_transcribe(
        JNIEnv *env,
        jobject thiz,
        jstring modelPath,
        jfloatArray pcmData) {

    const char *model_path =
            env->GetStringUTFChars(modelPath, nullptr);

    // モデルロード
    whisper_context *ctx =
            whisper_init_from_file(model_path);

    if (!ctx) {
        return env->NewStringUTF("model load failed");
    }

    // PCM取得
    jsize length = env->GetArrayLength(pcmData);

    jfloat *pcm =
            env->GetFloatArrayElements(pcmData, nullptr);

    // パラメータ
    whisper_full_params params =
            whisper_full_default_params(
                    WHISPER_SAMPLING_GREEDY);

    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;

    // 推論
    int result =
            whisper_full(ctx, params, pcm, length);

    std::string text;

    if (result == 0) {

        int n =
                whisper_full_n_segments(ctx);

        for (int i = 0; i < n; i++) {

            text += whisper_full_get_segment_text(
                    ctx, i);
        }
    }

    whisper_free(ctx);

    env->ReleaseFloatArrayElements(
            pcmData, pcm, 0);

    env->ReleaseStringUTFChars(
            modelPath, model_path);

    return env->NewStringUTF(text.c_str());
}