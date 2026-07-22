#pragma once

#include <jni.h>

#include <cstddef>
#include <vector>

namespace app::native_audio {

// JNI配列を一度に読み込むPCM16サンプル数。スタック使用量とJNI呼び出し回数を調整する。
inline constexpr std::size_t kPcmConversionChunkSamples = 4096;

/**
 * JavaのPCM16配列を4096サンプル単位のスタックバッファでfloatへ正規化します。
 * env例: JNIから渡されたJNIEnv、input例: short[]{0, 16384, -32768}相当、
 * output例: 再利用vector、requested_length例: 16000（-1なら全配列）。戻り値なし。
 * inputがnullの場合とJNI例外発生時はoutputを空にし、Java側のJNI例外は維持します。
 */
void pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        std::vector<float>& output,
        jsize requested_length = -1);

/**
 * 戻り値vectorを生成する互換API。env/input/requested_lengthの例と例外条件は
 * output参照版と同じで、戻り値例は{0.0f, 0.5f, -1.0f}。
 */
std::vector<float> pcm16_to_float_vector(
        JNIEnv* env,
        jshortArray input,
        jsize requested_length = -1);

}  // namespace app::native_audio
