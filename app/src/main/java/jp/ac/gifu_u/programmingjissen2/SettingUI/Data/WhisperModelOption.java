package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

/** Whisper で利用できるモデルの選択肢です。 */
public enum WhisperModelOption {
    BASE("base", "base", "ggml-base.bin", "速さ優先。録音しながらの確認向き"),
    SMALL("small", "small", "ggml-small.bin", "精度優先。端末によっては処理が重い");

    private final String key;
    private final String displayName;
    private final String assetName;
    private final String description;

    WhisperModelOption(final String key, final String displayName, final String assetName, final String description) {
        this.key = key;
        this.displayName = displayName;
        this.assetName = assetName;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String assetName() {
        return assetName;
    }

    public String description() {
        return description;
    }

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
