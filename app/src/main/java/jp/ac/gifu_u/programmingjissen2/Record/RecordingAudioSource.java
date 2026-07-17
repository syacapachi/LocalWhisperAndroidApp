package jp.ac.gifu_u.programmingjissen2.Record;

import androidx.annotation.NonNull;

/** 録音時に使用するAndroid音声入力の組み合わせです。 */
public enum RecordingAudioSource {
    MICROPHONE("マイクのみ", false),
    APP_CAPTURE("特定のアプリのキャプチャのみ", true),
    MICROPHONE_AND_APP("マイクとキャプチャ", true);

    private final String displayName;
    private final boolean appCaptureRequired;

    RecordingAudioSource(final String displayName, final boolean appCaptureRequired) {
        this.displayName = displayName;
        this.appCaptureRequired = appCaptureRequired;
    }

    /** @return アプリ音声キャプチャが必要ならtrue。例: {@code true} */
    public boolean requiresAppCapture() {
        return appCaptureRequired;
    }

    /**
     * Intent保存値から音声入力を復元します。
     * @param value enum名。例: {@code "APP_CAPTURE"}
     * @return 復元した入力。例: {@code RecordingAudioSource.APP_CAPTURE}
     * @throws IllegalArgumentException 未知のenum名の場合
     */
    @NonNull
    public static RecordingAudioSource fromName(final String value) {
        if (value == null) {
            return MICROPHONE;
        }
        return valueOf(value);
    }

    /** @return プルダウン用名称。例: {@code "マイクのみ"} */
    @NonNull
    @Override
    public String toString() {
        return displayName;
    }
}
