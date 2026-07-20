#pragma once

#include <cstddef>
#include <string>
#include <unordered_map>
#include <vector>

namespace app::ctranslate2_jni {

/**
 * Hugging FaceのWhisper tokenizer.jsonを読み、初期プロンプトをbyte-level BPEへ変換する。
 * model_directory例: "/data/.../openai-whisper-small-int8"。ファイルが不正なら例外を送出する。
 */
class WhisperPromptTokenizer {
public:
  explicit WhisperPromptTokenizer(const std::string& model_directory);

  /**
   * UTF-8テキストをCTranslate2へ渡せるtoken文字列へ変換する。
   * text例: "岐阜大学"、max_tokens例: 224。戻り値例: {"å²", "岐"}。
   * max_tokensが0なら空配列を返し、変換不能なtokenがあれば例外を送出する。
   */
  std::vector<std::string> encode(const std::string& text, size_t max_tokens) const;

private:
  std::unordered_map<std::string, size_t> merge_ranks_;
  std::unordered_map<unsigned char, std::string> byte_encoder_;
  std::unordered_map<std::string, size_t> vocabulary_;
};

}  // namespace app::ctranslate2_jni
