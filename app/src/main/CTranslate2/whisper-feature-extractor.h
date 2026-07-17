#pragma once

#include <cstddef>
#include <vector>

namespace app::ctranslate2_jni {

/**
 * 16kHz PCMをWhisper互換log-Melへ変換する。
 * samples例: 80000要素、n_mels例: 80。戻り値例: 80*3000要素。
 * n_melsが0の場合はstd::invalid_argumentを送出する。
 */
std::vector<float> make_whisper_features(const std::vector<float>& samples, size_t n_mels);

}  // namespace app::ctranslate2_jni
