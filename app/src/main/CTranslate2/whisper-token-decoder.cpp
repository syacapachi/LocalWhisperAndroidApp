#include "whisper-token-decoder.h"

#include <unordered_map>

namespace app::ctranslate2_jni {
namespace {

std::vector<unsigned int> utf8_code_points(const std::string& text) {
  std::vector<unsigned int> points;
  for (size_t i = 0; i < text.size();) {
    const auto first = static_cast<unsigned char>(text[i++]);
    if (first < 0x80) {
      points.push_back(first);
      continue;
    }
    const int trailing = first < 0xE0 ? 1 : (first < 0xF0 ? 2 : 3);
    unsigned int point = first & (0x7F >> trailing);
    for (int j = 0; j < trailing && i < text.size(); ++j)
      point = (point << 6) | (static_cast<unsigned char>(text[i++]) & 0x3F);
    points.push_back(point);
  }
  return points;
}
/** unicodeに対応するcharを返すデコーダを作成する。 static const で保持すること推奨 */
std::unordered_map<unsigned int, unsigned char> byte_decoder() {
  std::vector<unsigned int> bytes;
  for (unsigned int value = 33; value <= 126; ++value) bytes.push_back(value);
  for (unsigned int value = 161; value <= 172; ++value) bytes.push_back(value);
  for (unsigned int value = 174; value <= 255; ++value) bytes.push_back(value);
  std::vector<unsigned int> unicode = bytes;
  unsigned int extra = 0;
  for (unsigned int value = 0; value < 256; ++value) {
    bool included = false;
    for (const auto byte : bytes) included |= byte == value;
    if (!included) {
      bytes.push_back(value);
      unicode.push_back(256 + extra++);
    }
  }
  std::unordered_map<unsigned int, unsigned char> decoder;
  for (size_t i = 0; i < bytes.size(); ++i)
    decoder.emplace(unicode[i], static_cast<unsigned char>(bytes[i]));
  return decoder;
}

}  // namespace
/** 出力されてたトークン列を文字列に変換します。 */
std::string decode_whisper_tokens(const std::vector<std::string>& tokens) {
  static const auto decoder = byte_decoder();
  std::string output;
  for (const auto& token : tokens) {
    if (token.size() >= 4 && token.rfind("<|", 0) == 0)
      continue;
    for (const auto point : utf8_code_points(token)) {
      const auto found = decoder.find(point);
      if (found != decoder.end())
        output.push_back(static_cast<char>(found->second));
    }
  }
  return output;
}

}  // namespace app::ctranslate2_jni
