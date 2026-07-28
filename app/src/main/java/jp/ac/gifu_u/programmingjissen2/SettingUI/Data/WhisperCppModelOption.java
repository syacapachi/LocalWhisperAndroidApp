package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

/** ファイル一括文字起こしで使用するWhisper.cpp量子化モデルです。 */
public enum WhisperCppModelOption implements ITranscriptionModel {
    BASE_Q8_0("cpp-base-q8-0", "ggml-base_q8_0.bin", "Whisper.cpp base・Q8_0"),
    SMALL_Q8_0("cpp-small-q8-0", "ggml-small_q8_0.bin", "Whisper.cpp small・Q8_0");

    private final String key;
    private final String assetName;
    private final String displayName;

    /**
     * ファイル推論モデルの選択肢を定義します。
     * @param key SharedPreferences保存キー。例: {@code "cpp-small-q8-0"}
     * @param assetName assets内のファイル名。例: {@code "ggml-small_q8_0.bin"}
     * @param displayName UI表示名。例: {@code "Whisper.cpp small・Q8_0"}
     */
    WhisperCppModelOption(
            final String key,
            final String assetName,
            final String displayName
    ) {
        this.key = key;
        this.assetName = assetName;
        this.displayName = displayName;
    }

    /** @return 保存キー。例: {@code "cpp-small-q8-0"} */
    @NonNull
    public String key() { return key; }

    /** @return UI名。例: {@code "Whisper.cpp small・Q8_0"} */
    @Override @NonNull public String label() { return displayName; }

    /** @return assets内のモデル名。例: {@code "ggml-small_q8_0.bin"} */
    public String assetName() { return assetName; }

    /** @return assets相対モデルパス。例: {@code "ggml-small_q8_0.bin"} */
    @Override @NonNull public String modelPath() { return assetName; }

    /** @return Whisper.cpp */
    @Override @NonNull
    public WhisperInferenceEngine engine() { return WhisperInferenceEngine.WHISPER_CPP; }

    /** @return Whisper.cppでは未使用のため空文字 */
    @Override @NonNull public String computeType() { return ""; }

    /** @return assets同梱モデルなので常にtrue */
    @Override public boolean bundled() { return true; }

    /** @return UI表示名。例: {@code "Whisper.cpp small・Q8_0"} */
    @Override @NonNull public String displayName() { return displayName; }

    /** @return UI表示名。例: {@code "Whisper.cpp small・Q8_0"} */
    @NonNull
    @Override
    public String toString() { return displayName; }
}
