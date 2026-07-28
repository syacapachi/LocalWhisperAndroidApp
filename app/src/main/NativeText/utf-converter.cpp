#include "utf-converter.h"

#include <stdexcept>

namespace app::native_text {
namespace {

constexpr char32_t replacement_character = 0xFFFD;

/**
 * UnicodeコードポイントをUTF-16へ追加します。
 *
 * @param point Unicodeコードポイント。例: 0x1F600
 * @param output 追加先。例: u""
 * @return なし。例: pointが0x1F600ならサロゲートペアを追加
 * @throws std::bad_alloc outputの拡張に失敗した場合
 */
void append_utf16(const char32_t point, std::u16string& output) {
  if (point <= 0xFFFF) {
    output.push_back(static_cast<char16_t>(point));
    return;
  }
  const char32_t value = point - 0x10000;
  output.push_back(static_cast<char16_t>(0xD800 + (value >> 10)));
  output.push_back(static_cast<char16_t>(0xDC00 + (value & 0x3FF)));
}

/**
 * Unicodeコードポイントを通常UTF-8へ追加します。
 *
 * @param point Unicodeコードポイント。例: 0x3042
 * @param output 追加先。例: ""
 * @return なし。例: pointが0x3042なら3バイトを追加
 * @throws std::bad_alloc outputの拡張に失敗した場合
 */
void append_utf8(const char32_t point, std::string& output) {
  if (point <= 0x7F) {
    output.push_back(static_cast<char>(point));
  } else if (point <= 0x7FF) {
    output.push_back(static_cast<char>(0xC0 | (point >> 6)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  } else if (point <= 0xFFFF) {
    output.push_back(static_cast<char>(0xE0 | (point >> 12)));
    output.push_back(static_cast<char>(0x80 | ((point >> 6) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  } else {
    output.push_back(static_cast<char>(0xF0 | (point >> 18)));
    output.push_back(static_cast<char>(0x80 | ((point >> 12) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | ((point >> 6) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  }
}

/**
 * バイトがUTF-8の継続バイトか判定します。
 *
 * @param value 判定するバイト。例: 0x82
 * @return 0x80～0xBFならtrue。例: 0x82ならtrue
 * @throws なし
 */
bool is_continuation(const unsigned char value) noexcept {
  return (value & 0xC0) == 0x80;
}

}  // namespace

std::u16string utf8_to_utf16_replacing_invalid(const std::string& text) {
  std::u16string output;
  output.reserve(text.size());

  for (std::size_t index = 0; index < text.size();) {
    const auto first = static_cast<unsigned char>(text[index]);
    if (first <= 0x7F) {
      output.push_back(static_cast<char16_t>(first));
      ++index;
      continue;
    }

    std::size_t sequence_length = 0;
    char32_t point = 0;
    if (first >= 0xC2 && first <= 0xDF) {
      sequence_length = 2;
      point = first & 0x1F;
    } else if (first >= 0xE0 && first <= 0xEF) {
      sequence_length = 3;
      point = first & 0x0F;
    } else if (first >= 0xF0 && first <= 0xF4) {
      sequence_length = 4;
      point = first & 0x07;
    } else {
      append_utf16(replacement_character, output);
      ++index;
      continue;
    }

    std::size_t valid_length = 1;
    while (valid_length < sequence_length
           && index + valid_length < text.size()
           && is_continuation(static_cast<unsigned char>(text[index + valid_length]))) {
      ++valid_length;
    }
    if (valid_length != sequence_length) {
      append_utf16(replacement_character, output);
      index += valid_length;
      continue;
    }

    const auto second = static_cast<unsigned char>(text[index + 1]);
    const bool invalid_range =
        (first == 0xE0 && second < 0xA0)
        || (first == 0xED && second > 0x9F)
        || (first == 0xF0 && second < 0x90)
        || (first == 0xF4 && second > 0x8F);
    if (invalid_range) {
      append_utf16(replacement_character, output);
      index += sequence_length;
      continue;
    }

    for (std::size_t offset = 1; offset < sequence_length; ++offset) {
      point = (point << 6)
              | (static_cast<unsigned char>(text[index + offset]) & 0x3F);
    }
    append_utf16(point, output);
    index += sequence_length;
  }
  return output;
}

std::string utf16_to_utf8_replacing_invalid(
    const std::uint16_t* text,
    const std::size_t length) {
  if (text == nullptr && length != 0)
    throw std::invalid_argument("UTF-16 text must not be null when length is nonzero");

  std::string output;
  output.reserve(length * 3);
  for (std::size_t index = 0; index < length; ++index) {
    const std::uint16_t first = text[index];
    char32_t point;
    if (first >= 0xD800 && first <= 0xDBFF) {
      if (index + 1 < length
          && text[index + 1] >= 0xDC00
          && text[index + 1] <= 0xDFFF) {
        point = 0x10000
                + ((static_cast<char32_t>(first) - 0xD800) << 10)
                + (static_cast<char32_t>(text[++index]) - 0xDC00);
      } else {
        point = replacement_character;
      }
    } else if (first >= 0xDC00 && first <= 0xDFFF) {
      point = replacement_character;
    } else {
      point = first;
    }
    append_utf8(point, output);
  }
  return output;
}

}  // namespace app::native_text
