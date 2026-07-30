package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

/** Whisper で利用できるモデルの選択肢です。 */
public enum WhisperModelOption implements ITranscriptionModel {
    CT2_BASE_INT8("ct2-openai-base-int8", "ctranslate2/openai-whisper-base-int8",
            "openai-whisper-base-int8", "int8", "CTranslate2 openai/whisper-base・int8"),
    CT2_SMALL_INT8("ct2-openai-small-int8", "ctranslate2/openai-whisper-small-int8",
            "openai-whisper-small-int8", "int8", "CTranslate2 openai/whisper-small・int8");

    private final String key;
    private final String assetPath;
    private final String displayName;
    private final String computeType;
    private final String description;

    /**
     * モデル選択肢を定義します。
     * @param key 保存キー。例: {@code "ct2-openai-base-int8"}
     * @param assetPath assets相対パス。例: {@code "ctranslate2/openai-whisper-base-int8"}
     * @param displayName 標準名。例: {@code openai-whisper-small-int8}
     * @param computeType 計算型。例: {@code "int8"}
     * @param description 説明。例: {@code "CTranslate2 base・int8"}
     */
    WhisperModelOption(
            final String key,
            final String assetPath,
            final String displayName,
            final String computeType,
            final String description
    ) {
        this.key = key;
        this.assetPath = assetPath;
        this.displayName = displayName;
        this.computeType = computeType;
        this.description = description;
    }

    /** @return 設定保存キー。例: {@code "ggml-base-q8-0"} */
    @NonNull
    public String key() {
        return key;
    }

    /** @return モデルの説明名。例: {@code "CTranslate2 openai/whisper-small・int8"} */
    @Override
    @NonNull
    public String label() { return description; }

    /** @return UIへ表示するassetsパス。例: {@code "assets/ggml-base.bin"} */
    @NonNull
    public String displayName() {
        return displayName;
    }

    /** @return assets相対パス。例: {@code "ggml-base.bin"} */
    public String assetName() {
        return assetPath;
    }

    /** @return assets相対モデルパス。例: {@code "ctranslate2/openai-whisper-small-int8"} */
    @Override
    @NonNull
    public String modelPath() { return assetPath; }

    /**
     * assets内の変換済みCTranslate2モデルディレクトリを返します。
     * @return ディレクトリ。例: {@code "ctranslate2/base"}
     */
    public String cTranslate2AssetDirectory() {
        return assetPath;
    }

    /** @return 推論ランタイム。例: {@code WhisperInferenceEngine.CTRANSLATE2} */
    @NonNull
    @Override public WhisperInferenceEngine engine() { return WhisperInferenceEngine.CTRANSLATE2; }

    /** @return CTranslate2計算型。例: {@code "int8"} */
    @NonNull
    @Override public String computeType() { return computeType; }

    /** @return assets同梱モデルなので常にtrue */
    @Override public boolean bundled() { return true; }

    /** @return モデル形式と量子化の説明。例: {@code "Whisper.cpp base・Q8_0量子化"} */
    public String description() {
        return description;
    }

    /** @return ドロップダウン表示。例: {@code "assets/ggml-base_q8_0.bin"} */
    @NonNull
    @Override
    public String toString() { return displayName(); }

}
