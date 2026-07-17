#pragma once

#include <string>
#include <vector>

namespace app::ctranslate2_jni {

/**
 * Whisperのbyte-level BPEトークンをUTF-8本文へ戻す。
 * tokens例: {"ãģĵ"}。戻り値例: 日本語UTF-8文字列。例外は送出しない。
 */
std::string decode_whisper_tokens(const std::vector<std::string>& tokens);

}  // namespace app::ctranslate2_jni
