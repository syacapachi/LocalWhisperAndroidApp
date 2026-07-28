package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

/** モデルファイルを実行する推論ランタイムです。 */
public enum WhisperInferenceEngine {
    /** 単一ggmlファイルをwhisper.cppで実行します。 */
    WHISPER_CPP,
    /** 変換済みモデルディレクトリをCTranslate2で実行します。 */
    CTRANSLATE2;

    /** @return JSON用実行モデル名。例: {@code "CTranslate2"} */
    @NonNull
    @Contract(pure = true)
    public String jsonValue() {
        return this == WHISPER_CPP ? "Whisper" : "CTranslate2";
    }

    /**
     * JSONの実行モデル名をenumへ変換します。
     * @param value JSON値。例: {@code "Whisper"}
     * @return 対応方式。例: {@code WHISPER_CPP}
     * @throws IllegalArgumentException 未対応値の場合
     */
    public static WhisperInferenceEngine fromJsonValue(final String value) {
        if ("Whisper".equalsIgnoreCase(value)) {
            return WHISPER_CPP;
        }
        if ("CTranslate2".equalsIgnoreCase(value)) {
            return CTRANSLATE2;
        }
        throw new IllegalArgumentException("Unknown execution model: " + value);
    }
}
