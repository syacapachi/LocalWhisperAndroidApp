package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

/** 録音画面に配置された Whisper 関連 UI 参照です。 */
public final class WhisperRecordControls {
    public final Button recordButton;
    public final Button inferenceButton;
    public final Button settingsButton;
    public final Spinner recordingSourceSpinner;
    public final Spinner captureTargetAppSpinner;
    public final RadioGroup modelRadioGroup;
    public final TextView resultTextView;
    public final TextView statusTextView;
    public final TextView benchmarkTextView;

    /**
     * 録音画面のUI参照をまとめます。
     *
     * @param recordButton 録音ボタン。例: {@code findViewById(R.id.recordButton)}
     * @param inferenceButton 推論ボタン。例: {@code findViewById(R.id.inferenceButton)}
     * @param settingsButton 設定ボタン。例: {@code findViewById(R.id.whisperSettingsButton)}
     * @param recordingSourceSpinner 入力選択。例: {@code findViewById(R.id.recordingSourceSpinner)}
     * @param captureTargetAppSpinner 対象アプリ選択。例: {@code findViewById(R.id.captureTargetAppSpinner)}
     * @param modelRadioGroup モデル選択。例: {@code findViewById(R.id.whisperModelRadioGroup)}
     * @param resultTextView 認識結果欄。例: {@code findViewById(R.id.recordText)}
     * @param statusTextView 状態欄。例: {@code findViewById(R.id.whisperStatusText)}
     * @param benchmarkTextView 統計欄。例: {@code findViewById(R.id.whisperBenchmarkText)}
     */
    public WhisperRecordControls(
            Button recordButton,
            Button inferenceButton,
            Button settingsButton,
            Spinner recordingSourceSpinner,
            Spinner captureTargetAppSpinner,
            RadioGroup modelRadioGroup,
            TextView resultTextView,
            TextView statusTextView,
            TextView benchmarkTextView
    ) {
        this.recordButton = recordButton;
        this.inferenceButton = inferenceButton;
        this.settingsButton = settingsButton;
        this.recordingSourceSpinner = recordingSourceSpinner;
        this.captureTargetAppSpinner = captureTargetAppSpinner;
        this.modelRadioGroup = modelRadioGroup;
        this.resultTextView = resultTextView;
        this.statusTextView = statusTextView;
        this.benchmarkTextView = benchmarkTextView;
    }
}
