#pragma once

#include <memory>
#include <string>
#include <vector>

#include <whisper.h>

namespace app::ctranslate2_jni {

struct WhisperVadContextDeleter {
  void operator()(whisper_vad_context* context) const noexcept;
};

using WhisperVadContextPtr =
    std::unique_ptr<whisper_vad_context, WhisperVadContextDeleter>;

// Silero VADモデルをCPU・1スレッドで読み込みます。
// model_path例: "/data/user/0/.../ggml-silero-v6.2.0.bin"。
// 戻り値例: 有効なWhisperVadContextPtr。読み込み失敗時はruntime_errorを送出します。
WhisperVadContextPtr create_whisper_vad_context(const std::string& model_path);

// 16kHz mono float PCMをSilero VADへ通し、発話区間だけをoutputへ連結します。
// samples例: 5秒音声のvector<float>(80000)、threshold例: 0.6f。
// 戻り値例: 発話ありならtrue、全区間が無音ならfalse。
// contextがnull、VAD実行失敗時はinvalid_argumentまたはruntime_errorを送出します。
bool collect_whisper_vad_speech(
    whisper_vad_context* context,
    const std::vector<float>& samples,
    float threshold,
    std::vector<float>& output);

}  // namespace app::ctranslate2_jni
