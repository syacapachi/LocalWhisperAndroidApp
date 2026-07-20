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
 * input例: short[]{0, 16384, -32768}相当、戻り値例: {0.0f, 0.5f, -1.0f}。
 * inputがnullの場合は空配列を返し、JNI例外発生時も空配列を返します。
 */
inline std::vector<float> pcm16_to_float_vector(JNIEnv* env, jshortArray input) {
    if (input == nullptr) {
        return {};
    }
    const jsize length = env->GetArrayLength(input);
    std::vector<float> output(static_cast<std::size_t>(std::max<jsize>(0, length)));
    std::array<jshort, kPcmConversionChunkSamples> pcm_chunk{};
    std::array<float, kPcmConversionChunkSamples> float_chunk{};

    for (jsize offset = 0; offset < length; offset += kPcmConversionChunkSamples) {
        const jsize count = std::min<jsize>(
                static_cast<jsize>(kPcmConversionChunkSamples), length - offset);
        env->GetShortArrayRegion(input, offset, count, pcm_chunk.data());
        if (env->ExceptionCheck()) {
            return {};
        }
        for (jsize index = 0; index < count; ++index) {
            float_chunk[static_cast<std::size_t>(index)] =
                    static_cast<float>(pcm_chunk[static_cast<std::size_t>(index)]) / 32768.0f;
        }
        std::copy_n(float_chunk.data(), count, output.data() + offset);
    }
    return output;
}

}  // namespace app::native_audio
