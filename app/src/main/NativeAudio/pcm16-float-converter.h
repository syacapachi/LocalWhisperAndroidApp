#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace app::native_audio {

/**
 * 最大2区間のPCM16ポインターを1本の再利用float vectorへ正規化します。
 * first例: {0, 16384}、first_count例: 2、second例: {-32768}、
 * second_count例: 1、output例: 再利用vector。戻り値なし。
 * countが正のポインターがnullの場合はstd::invalid_argument、
 * 合計要素数がsize_t範囲を超える場合はstd::length_errorを送出します。
 */
void pcm16_spans_to_float_vector(
        const std::int16_t* first,
        std::size_t first_count,
        const std::int16_t* second,
        std::size_t second_count,
        std::vector<float>& output);

}  // namespace app::native_audio
