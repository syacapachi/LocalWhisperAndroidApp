package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import jp.ac.gifu_u.programmingjissen2.MainUI.MorphingControlButton;

/** 録音画面に配置された Whisper 関連 UI 参照です。 */
public final class WhisperRecordControls {
    public final MorphingControlButton recordButton;
    public final MorphingControlButton recordingPauseButton;
    public final MorphingControlButton inferenceButton;
    public final Button settingsButton;
    public final Spinner recordingSourceSpinner;
    public final TextView resultTextView;
    public final TextView statusTextView;
    public final TextView benchmarkTextView;

    /**
     * 録音画面のUI参照をまとめます。
     *
     * @param recordButton 録音ボタン。例: {@code findViewById(R.id.recordButton)}
     * @param recordingPauseButton 録音一時停止・再開ボタン。例: {@code findViewById(R.id.recordingPauseButton)}
     * @param inferenceButton 推論一時停止・再開ボタン。例: {@code findViewById(R.id.inferenceButton)}
     * @param settingsButton 設定ボタン。例: {@code findViewById(R.id.whisperSettingsButton)}
     * @param recordingSourceSpinner 入力選択。例: {@code findViewById(R.id.recordingSourceSpinner)}
     * @param resultTextView 認識結果欄。例: {@code findViewById(R.id.recordText)}
     * @param statusTextView 状態欄。例: {@code findViewById(R.id.whisperStatusText)}
     * @param benchmarkTextView 統計欄。例: {@code findViewById(R.id.whisperBenchmarkText)}
     */
    public WhisperRecordControls(
            MorphingControlButton recordButton,
            MorphingControlButton recordingPauseButton,
            MorphingControlButton inferenceButton,
            Button settingsButton,
            Spinner recordingSourceSpinner,
            TextView resultTextView,
            TextView statusTextView,
            TextView benchmarkTextView
    ) {
        this.recordButton = recordButton;
        this.recordingPauseButton = recordingPauseButton;
        this.inferenceButton = inferenceButton;
        this.settingsButton = settingsButton;
        this.recordingSourceSpinner = recordingSourceSpinner;
        this.resultTextView = resultTextView;
        this.statusTextView = statusTextView;
        this.benchmarkTextView = benchmarkTextView;
    }
}
