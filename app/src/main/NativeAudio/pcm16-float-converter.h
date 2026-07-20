#pragma once

#include <jni.h>

#include <algorithm>
#include <array>
#include <cstddef>
#include <vector>

namespace app::native_audio {

constexpr std::size_t kPcmConversionChunkSamples = 4096;

/**
 * JavaのPCM16配列を4096サンプル単位のスタックバッファでfloatへ正規化します。
 * env例: JNIから渡されたJNIEnv、input例: short[]{0, 16384, -32768}相当、
 * output例: 再利用vector、requested_length例: 16000（-1なら全配列）。戻り値なし。
 * inputがnullの場合とJNI例外発生時はoutputを空にし、Java側のJNI例外は維持します。
 */
inline void pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        std::vector<float>& output,
        jsize requested_length = -1) {
    if (input == nullptr) {
        output.clear();
        return;
    }
    const jsize array_length = env->GetArrayLength(input);
    const jsize length = requested_length < 0
            ? array_length
            : std::min(array_length, requested_length);
    output.resize(static_cast<std::size_t>(std::max<jsize>(0, length)));
    std::array<jshort, kPcmConversionChunkSamples> pcm_chunk{};
    std::array<float, kPcmConversionChunkSamples> float_chunk{};

    for (jsize offset = 0; offset < length; offset += kPcmConversionChunkSamples) {
        const jsize count = std::min<jsize>(
                static_cast<jsize>(kPcmConversionChunkSamples), length - offset);
        env->GetShortArrayRegion(input, offset, count, pcm_chunk.data());
        if (env->ExceptionCheck()) {
            output.clear();
            return;
        }
        for (jsize index = 0; index < count; ++index) {
            float_chunk[static_cast<std::size_t>(index)] =
                    static_cast<float>(pcm_chunk[static_cast<std::size_t>(index)]) / 32768.0f;
        }
        std::copy_n(float_chunk.data(), count, output.data() + offset);
    }
}

/**
 * 戻り値vectorを生成する互換API。env/input/requested_lengthの例と例外条件は
 * output参照版と同じで、戻り値例は{0.0f, 0.5f, -1.0f}。
 */
inline std::vector<float> pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        jsize requested_length = -1) {
    std::vector<float> output;
    pcm16_to_float_vector(env, input, output, requested_length);
    return output;
}

}  // namespace app::native_audio
