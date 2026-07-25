#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace app::native_text {

/**
 * 通常UTF-8をUTF-16へ変換します。
 *
 * @param text UTF-8文字列。例: "\xE3\x81\x82"
 * @return UTF-16文字列。例: u"あ"
 * @throws std::bad_alloc 変換先のメモリ確保に失敗した場合
 *
 * 不正または途中で終了したUTF-8シーケンスはU+FFFDへ置換します。
 */
std::u16string utf8_to_utf16_replacing_invalid(const std::string& text);

/**
 * UTF-16を通常UTF-8へ変換します。
 *
 * @param text UTF-16コード単位。例: u"あ"
 * @param length textのコード単位数。例: 1
 * @return UTF-8文字列。例: "\xE3\x81\x82"
 * @throws std::invalid_argument textがnullかつlengthが0より大きい場合
 * @throws std::bad_alloc 変換先のメモリ確保に失敗した場合
 *
 * 対応しないサロゲートはU+FFFDへ置換します。
 */
std::string utf16_to_utf8_replacing_invalid(
    const std::uint16_t* text,
    std::size_t length);

}  // namespace app::native_text
