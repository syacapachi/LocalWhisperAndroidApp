package jp.ac.gifu_u.programmingjissen2.SettingUI;

import androidx.annotation.NonNull;

/** Whisper の language パラメータに渡す値と、設定 UI に表示する文字を対応させます。 */
public enum WhisperLanguageOption {
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "英語"),
    AUTO("auto", "自動判定"),
    CHINESE("zh", "中国語"),
    KOREAN("ko", "韓国語"),
    SPANISH("es", "スペイン語"),
    FRENCH("fr", "フランス語"),
    GERMAN("de", "ドイツ語");

    private final String value;
    private final String displayText;

    WhisperLanguageOption(final String value, final String displayText) {
        this.value = value;
        this.displayText = displayText;
    }

    /** Whisper.cpp に渡す language の値です。 */
    public String value() {
        return value;
    }

    /** 設定 UI に表示する文字です。 */
    public String displayText() {
        return displayText;
    }

    /** 保存済み文字列から対応する enum を返します。 */
    public static WhisperLanguageOption fromValue(final String value) {
        if (value != null) {
            for (WhisperLanguageOption option : values()) {
                if (option.value.equals(value.trim())) {
                    return option;
                }
            }
        }
        return JAPANESE;
    }

    @NonNull
    @Override
    public String toString() {
        return displayText + " (" + value + ")";
    }
}
