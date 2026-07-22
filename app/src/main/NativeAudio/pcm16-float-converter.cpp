#include "pcm16-float-converter.h"

#include <algorithm>
#include <array>
#include <cstdint>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define APP_PCM_HAS_NEON 1
#else
#define APP_PCM_HAS_NEON 0
#endif

namespace app::native_audio {
namespace {

// PCM16の振幅を[-1, 1)へ正規化するための32768の逆数。
constexpr float kInvPcm16Amplitude = 1.0f / 32768.0f;
// 128-bit NEONレジスタ2本で一度に変換するPCM16サンプル数。
constexpr std::size_t kNeonSamplesPerIteration = 8;

/**
 * PCM16を[-1, 1)のfloatへ変換する。
 * __ARM_NEON__が有効な場合は、並列実行を行う。
 * input例: {0, 16384, -32768}、output例: 3要素の書込先、count例: 3。
 * 戻り値なし。input/outputが有効なcount要素を持たない場合の動作は未定義で、例外は送出しない。
 */
void convert_pcm16_chunk(
        const jshort* input,
        float* output,
        const std::size_t count) noexcept {
    std::size_t index = 0;
#if APP_PCM_HAS_NEON
    // NEONがある場合、並列実行できる。同時に[kNeonSamplesPerIteration = 8]個
    // float4つに  に同じ値を入れる。
    const float32x4_t scale = vdupq_n_f32(kInvPcm16Amplitude);
    for (; index + kNeonSamplesPerIteration <= count;
         index += kNeonSamplesPerIteration) {
        // ポインターから、short 8つを取り出す。
        const int16x8_t pcm = vld1q_s16(
                reinterpret_cast<const std::int16_t*>(input + index));
        // 8つを４つづつ(上位４つ、下位４つにわける、)その後 short -> int に拡張
        const int32x4_t low = vmovl_s16(vget_low_s16(pcm));
        const int32x4_t high = vmovl_s16(vget_high_s16(pcm));

        // vcvtq_f32_s32でint->float
        // vmulq_f32でかけ算
        // vmulq_f32で指定アドレス書き込み
        vst1q_f32(output + index, vmulq_f32(vcvtq_f32_s32(low), scale));
        vst1q_f32(output + index + 4, vmulq_f32(vcvtq_f32_s32(high), scale));
    }
#endif
    // 残りを処理(並列実行できない場合もこっち)
    for (; index < count; ++index)
        output[index] = static_cast<float>(input[index]) * kInvPcm16Amplitude;
}

}  // namespace
/**
 * @param env Javaのアドレス
 * @param input short[]のアドレス
 * @param output 出力先vector
 * @param requested_length 変換する長さ
 */
void pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        std::vector<float>& output,
        const jsize requested_length) {
    static_assert(sizeof(jshort) == sizeof(std::int16_t),
                  "jshort must be a 16-bit integer");
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

    for (jsize offset = 0; offset < length;
         offset += static_cast<jsize>(kPcmConversionChunkSamples)) {
        const jsize count = std::min<jsize>(
                static_cast<jsize>(kPcmConversionChunkSamples), length - offset);
        env->GetShortArrayRegion(input, offset, count, pcm_chunk.data());
        if (env->ExceptionCheck()) {
            output.clear();
            return;
        }
        convert_pcm16_chunk(
                pcm_chunk.data(),
                output.data() + static_cast<std::size_t>(offset),
                static_cast<std::size_t>(count));
    }
}

std::vector<float> pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        const jsize requested_length) {
    std::vector<float> output;
    pcm16_to_float_vector(env, input, output, requested_length);
    return output;
}

}  // namespace app::native_audio
