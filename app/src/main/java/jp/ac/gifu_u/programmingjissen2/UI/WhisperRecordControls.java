package jp.ac.gifu_u.programmingjissen2.UI;

import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

/** 録音画面に配置された Whisper 関連 UI 参照です。 */
public class WhisperRecordControls {
    public final Button recordButton;
    public final Button settingsButton;
    public final RadioGroup modelRadioGroup;
    public final TextView resultTextView;
    public final TextView statusTextView;
    public final TextView benchmarkTextView;

    public WhisperRecordControls(
            Button recordButton,
            Button settingsButton,
            RadioGroup modelRadioGroup,
            TextView resultTextView,
            TextView statusTextView,
            TextView benchmarkTextView
    ) {
        this.recordButton = recordButton;
        this.settingsButton = settingsButton;
        this.modelRadioGroup = modelRadioGroup;
        this.resultTextView = resultTextView;
        this.statusTextView = statusTextView;
        this.benchmarkTextView = benchmarkTextView;
    }
}
