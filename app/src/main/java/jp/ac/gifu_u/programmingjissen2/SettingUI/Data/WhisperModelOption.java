package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

/** Whisper で利用できるモデルの選択肢です。 */
public enum WhisperModelOption {
    CT2_BASE_INT8("ct2-openai-base-int8", "ctranslate2/openai-whisper-base-int8",
            WhisperInferenceEngine.CTRANSLATE2, "int8", "CTranslate2 openai/whisper-base・int8"),
    CT2_SMALL_INT8("ct2-openai-small-int8", "ctranslate2/openai-whisper-small-int8",
            WhisperInferenceEngine.CTRANSLATE2, "int8", "CTranslate2 openai/whisper-small・int8"),
    CT2_MEDIUM_INT8("ct2-openai-medium-int8", "ctranslate2/openai-whisper-medium-int8",
            WhisperInferenceEngine.CTRANSLATE2, "int8", "CTranslate2 openai/whisper-medium・int8"),
    CT2_KOTOBA_V2_2_INT8("ct2-kotoba-v2-2-int8", "ctranslate2/kotoba-whisper-v2.2-int8",
            WhisperInferenceEngine.CTRANSLATE2, "int8", "CTranslate2 kotoba-whisper-v2.2・int8");

    private final String key;
    private final String assetPath;
    private final String displayName;
    private final WhisperInferenceEngine engine;
    private final String computeType;
    private final String description;

    /**
     * モデル選択肢を定義します。
     * @param key 保存キー。例: {@code "ct2-openai-base-int8"}
     * @param assetPath assets相対パス。例: {@code "ctranslate2/openai-whisper-base-int8"}
     * @param engine 推論系。例: {@code WhisperInferenceEngine.CTRANSLATE2}
     * @param computeType 計算型。例: {@code "int8"}
     * @param description 説明。例: {@code "CTranslate2 base・int8"}
     */
    WhisperModelOption(
            final String key,
            final String assetPath,
            final WhisperInferenceEngine engine,
            final String computeType,
            final String description
    ) {
        this.key = key;
        this.assetPath = assetPath;
        this.displayName = "assets/" + assetPath;
        this.engine = engine;
        this.computeType = computeType;
        this.description = description;
    }

    /** @return 設定保存キー。例: {@code "ggml-base-q8-0"} */
    public String key() {
        return key;
    }

    /** @return UIへ表示するassetsパス。例: {@code "assets/ggml-base.bin"} */
    @NonNull
    public String displayName() {
        return displayName;
    }

    /** @return assets相対パス。例: {@code "ggml-base.bin"} */
    public String assetName() {
        return assetPath;
    }

    /**
     * assets内の変換済みCTranslate2モデルディレクトリを返します。
     * @return ディレクトリ。例: {@code "ctranslate2/base"}
     */
    public String cTranslate2AssetDirectory() {
        return assetPath;
    }

    /** @return 推論ランタイム。例: {@code WhisperInferenceEngine.CTRANSLATE2} */
    public WhisperInferenceEngine engine() { return engine; }

    /** @return CTranslate2計算型。例: {@code "int8"} */
    public String computeType() { return computeType; }

    /** @return モデル形式と量子化の説明。例: {@code "Whisper.cpp base・Q8_0量子化"} */
    public String description() {
        return description;
    }

    /** @return ドロップダウン表示。例: {@code "assets/ggml-base_q8_0.bin"} */
    @NonNull
    @Override
    public String toString() { return displayName(); }

    /**
     * 保存キーからモデルを復元します。
     * @param key 保存キー。例: {@code "ct2-openai-base-int8"}
     * @return 対応モデル。不明値は既定モデル。例: {@code WhisperModelOption.CT2_BASE_INT8}
     */
    @NonNull
    public static WhisperModelOption fromKey(final String key) {
        if (key != null) {
            for (WhisperModelOption value : values()) {
                if (value.key.equals(key)) {
                    return value;
                }
            }
        }
        return WhisperSettings.DEFAULT_MODEL;
    }
}
