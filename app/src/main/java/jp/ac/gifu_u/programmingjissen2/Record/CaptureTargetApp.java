package jp.ac.gifu_u.programmingjissen2.Record;

import androidx.annotation.NonNull;

/** 再生音声キャプチャの対象にするアプリ情報です。 */
public record CaptureTargetApp(
        @NonNull String label,
        @NonNull String packageName,
        int uid
) {
    /** @return アプリ選択プルダウン用名称。例: {@code "YouTube"} */
    @NonNull
    @Override
    public String toString() {
        return label;
    }
}
