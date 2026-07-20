#include "whisper-prompt-tokenizer.h"

#include <algorithm>
#include <fstream>
#include <limits>
#include <stdexcept>
#include <utility>

#include <nlohmann/json.hpp>

namespace app::ctranslate2_jni {
namespace {

/** code point例0x3042をUTF-8文字列例"あ"へ変換する。範囲外は例外を送出する。 */
std::string utf8_from_code_point(unsigned int point) {
  std::string output;
  if (point <= 0x7F) {
    output.push_back(static_cast<char>(point));
  } else if (point <= 0x7FF) {
    output.push_back(static_cast<char>(0xC0 | (point >> 6)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  } else if (point <= 0xFFFF) {
    output.push_back(static_cast<char>(0xE0 | (point >> 12)));
    output.push_back(static_cast<char>(0x80 | ((point >> 6) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  } else if (point <= 0x10FFFF) {
    output.push_back(static_cast<char>(0xF0 | (point >> 18)));
    output.push_back(static_cast<char>(0x80 | ((point >> 12) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | ((point >> 6) & 0x3F)));
    output.push_back(static_cast<char>(0x80 | (point & 0x3F)));
  } else {
    throw std::invalid_argument("invalid Unicode code point");
  }
  return output;
}

/** GPT-2/Whisper byte encoderを作る。引数なし。戻り値例: byte 32 -> "Ġ"。例外はメモリ不足時のみ。 */
std::unordered_map<unsigned char, std::string> make_byte_encoder() {
  std::vector<unsigned int> bytes;
  for (unsigned int value = 33; value <= 126; ++value) bytes.push_back(value);
  for (unsigned int value = 161; value <= 172; ++value) bytes.push_back(value);
  for (unsigned int value = 174; value <= 255; ++value) bytes.push_back(value);
  std::vector<unsigned int> unicode = bytes;
  unsigned int extra = 0;
  for (unsigned int value = 0; value < 256; ++value) {
    if (std::find(bytes.begin(), bytes.end(), value) == bytes.end()) {
      bytes.push_back(value);
      unicode.push_back(256 + extra++);
    }
  }
  std::unordered_map<unsigned char, std::string> encoder;
  for (size_t i = 0; i < bytes.size(); ++i)
    encoder.emplace(static_cast<unsigned char>(bytes[i]), utf8_from_code_point(unicode[i]));
  return encoder;
}

/** 左右token例"a","b"を衝突しないpair keyへする。戻り値例"a\0b"。例外はない。 */
std::string pair_key(const std::string& left, const std::string& right) {
  std::string key = left;
  key.push_back('\0');
  key.append(right);
  return key;
}

}  // namespace

WhisperPromptTokenizer::WhisperPromptTokenizer(const std::string& model_directory)
    : byte_encoder_(make_byte_encoder()) {
  std::ifstream stream(model_directory + "/tokenizer.json");
  if (!stream)
    throw std::runtime_error("tokenizer.json not found: " + model_directory);
  const auto document = nlohmann::json::parse(stream);
  const auto& model = document.at("model");
  vocabulary_ = model.at("vocab").get<std::unordered_map<std::string, size_t>>();
  const auto& merges = model.at("merges");
  if (merges.empty())
    throw std::runtime_error("BPE merges is empty in tokenizer.json");
  const auto merge_type = merges[0].type();
  const bool is_array = merge_type == nlohmann::json::value_t::array;
  if (!is_array && merge_type != nlohmann::json::value_t::string)
    throw std::runtime_error("unsupported BPE merge type in tokenizer.json");

  size_t rank = 0;
  if (is_array) {
    for (const auto& merge_value : merges) {
      if (merge_value.size() != 2)
        throw std::runtime_error("invalid array BPE merge in tokenizer.json");
      const std::string left = merge_value[0].get<std::string>();
      const std::string right = merge_value[1].get<std::string>();
      if (left.empty() || right.empty())
        throw std::runtime_error("empty BPE merge token in tokenizer.json");
      merge_ranks_.emplace(pair_key(left, right), rank++);
    }
  } else {
    for (const auto& merge_value : merges) {
      const std::string merge = merge_value.get<std::string>();
      const size_t separator = merge.find(' ');
      if (separator == std::string::npos)
        throw std::runtime_error("invalid string BPE merge in tokenizer.json");
      const std::string left = merge.substr(0, separator);
      const std::string right = merge.substr(separator + 1);
      if (left.empty() || right.empty())
        throw std::runtime_error("empty BPE merge token in tokenizer.json");
      merge_ranks_.emplace(pair_key(left, right), rank++);
    }
  }
}

std::vector<std::string> WhisperPromptTokenizer::encode(
    const std::string& text,
    const size_t max_tokens) const {
  if (text.empty() || max_tokens == 0)
    return {};

  std::vector<std::string> pieces;
  pieces.reserve(text.size());
  for (const unsigned char byte : text)
    pieces.push_back(byte_encoder_.at(byte));

  while (pieces.size() > 1) {
    size_t best_rank = std::numeric_limits<size_t>::max();
    size_t best_index = pieces.size();
    for (size_t index = 0; index + 1 < pieces.size(); ++index) {
      const auto found = merge_ranks_.find(pair_key(pieces[index], pieces[index + 1]));
      if (found != merge_ranks_.end() && found->second < best_rank) {
        best_rank = found->second;
        best_index = index;
      }
    }
    if (best_index == pieces.size())
      break;
    const std::string left = pieces[best_index];
    const std::string right = pieces[best_index + 1];
    for (size_t index = 0; index + 1 < pieces.size();) {
      if (pieces[index] == left && pieces[index + 1] == right) {
        pieces[index].append(pieces[index + 1]);
        pieces.erase(pieces.begin() + static_cast<std::ptrdiff_t>(index + 1));
      } else {
        ++index;
      }
    }
  }

  for (const auto& piece : pieces) {
    if (vocabulary_.find(piece) == vocabulary_.end())
      throw std::runtime_error("prompt token is not in Whisper vocabulary");
  }
  if (pieces.size() > max_tokens)
    pieces.erase(pieces.begin(), pieces.end() - static_cast<std::ptrdiff_t>(max_tokens));
  return pieces;
}

}  // namespace app::ctranslate2_jni
