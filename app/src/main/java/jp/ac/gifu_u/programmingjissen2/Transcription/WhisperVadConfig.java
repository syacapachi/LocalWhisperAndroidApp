package jp.ac.gifu_u.programmingjissen2.Transcription;

import androidx.annotation.NonNull;

import Whisper.WhisperBridge;

/** whisper.cpp 内蔵VADへ渡す共通設定です。 */
public final class WhisperVadConfig {
    /** アプリへ同梱する公式Silero VADモデルのasset名です。 */
    public static final String MODEL_ASSET_NAME = "ggml-silero-v6.2.0.bin";

    private WhisperVadConfig() {
    }

    /**
     * Whisperの一括推論パラメータで内蔵VADを有効化します。
     *
     * @param params 設定対象。例: {@code WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY)}
     * @param vadModelPath VADモデルの実ファイルパス。例: {@code "/data/user/0/.../files/ggml-silero-v6.2.0.bin"}
     * @return 設定後の同じインスタンス。例: {@code params}
     * @throws IllegalArgumentException paramsがnull、またはvadModelPathがnullか空文字の場合
     */
    @NonNull
    public static WhisperBridge.FullParams enable(
            @NonNull final WhisperBridge.FullParams params,
            final String vadModelPath
    ) {
        if (vadModelPath == null || vadModelPath.trim().isEmpty()) {
            throw new IllegalArgumentException("VAD model path must not be empty");
        }

        params.vad = true;
        params.vadModelPath = vadModelPath;
        return params;
    }
}
