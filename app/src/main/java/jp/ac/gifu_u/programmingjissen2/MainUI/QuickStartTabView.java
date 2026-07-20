package jp.ac.gifu_u.programmingjissen2.MainUI;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import events.Whisper.WhisperProgressEvent;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;

/** 録音開始に必要な操作と進捗、最新結果を一画面へまとめたタブです。 */
public final class QuickStartTabView {
    private final Activity activity;
    private final LinearLayout root;
    private final Button fileButton;
    private final Button recordButton;
    private final Spinner sourceSpinner;
    private final EditText windowEdit;
    private final EditText vadThresholdEdit;
    private final SwitchCompat recordAndRetranscribeSwitch;
    private final ProgressBar progressBar;
    private final TextView progressText;
    private final TextView latestText;
    private final TextView statusText;
    private final TextView benchmarkText;

    /** @param activity 親Activity。例: {@code mainActivity} */
    public QuickStartTabView(@NonNull final Activity activity) {
        this.activity = activity;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(12));

        fileButton = new Button(activity);
        fileButton.setText("ファイルから文字起こし");
        root.addView(fileButton, matchWrap());

        final ScrollView contentScroll = new ScrollView(activity);
        final LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        contentScroll.addView(content);
        root.addView(contentScroll, weighted());

        content.addView(section("進捗"));
        final LinearLayout progressRow = new LinearLayout(activity);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressBar = new ProgressBar(activity);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        progressRow.addView(progressBar, new LinearLayout.LayoutParams(dp(36), dp(36)));
        progressText = text("待機中", 14);
        final LinearLayout.LayoutParams progressTextParams =
                new LinearLayout.LayoutParams(0, -2, 1f);
        progressTextParams.setMargins(dp(8), 0, 0, 0);
        progressRow.addView(progressText, progressTextParams);
        content.addView(progressRow, matchWrap());

        content.addView(section("最新の文字起こし結果"));
        latestText = text("録音を開始すると、ここに最新結果を表示します。", 16);
        latestText.setMinHeight(dp(96));
        latestText.setMaxLines(6);
        latestText.setTextIsSelectable(true);
        latestText.setPadding(dp(12), dp(10), dp(12), dp(10));
        latestText.setBackgroundColor(0x0D000000);
        content.addView(latestText, matchWrap());

        content.addView(section("クイック設定"));
        content.addView(label("録音対象"));
        sourceSpinner = new Spinner(activity);
        content.addView(sourceSpinner, matchWrap());
        content.addView(label("推論窓の大きさ（ms）"));
        windowEdit = numberEdit("例: 5000", false);
        content.addView(windowEdit, matchWrap());
        content.addView(label("VADの閾値"));
        vadThresholdEdit = numberEdit("0.0～1.0（例: 0.6）", true);
        content.addView(vadThresholdEdit, matchWrap());
        recordAndRetranscribeSwitch = new SwitchCompat(activity);
        recordAndRetranscribeSwitch.setText("録音してあとで再推論");
        recordAndRetranscribeSwitch.setMinHeight(dp(48));
        content.addView(recordAndRetranscribeSwitch, matchWrap());

        statusText = text("状態: 待機中", 13);
        content.addView(statusText, matchWrap());
        content.addView(section("モデル別推論時間（デバッグ）"));
        benchmarkText = text("推論時間 未計測", 12);
        content.addView(benchmarkText, matchWrap());

        recordButton = new Button(activity);
        recordButton.setText("録音開始");
        recordButton.setMinHeight(dp(64));
        root.addView(recordButton, matchWrap());
    }

    /** @return タブへ追加するルートView。例: {@code LinearLayout} */
    @NonNull public View view() { return root; }

    /** @return ファイル選択を開始するButton。例: {@code fileButton} */
    @NonNull public Button fileButton() { return fileButton; }
    /** @return 推論窓入力欄。例: 内容{@code "5000"} */
    @NonNull public EditText windowEdit() { return windowEdit; }
    /** @return VAD閾値入力欄。例: 内容{@code "0.6"} */
    @NonNull public EditText vadThresholdEdit() { return vadThresholdEdit; }
    /** @return 録音保存・再推論Switch。例: {@code switchView} */
    @NonNull public SwitchCompat recordAndRetranscribeSwitch() {
        return recordAndRetranscribeSwitch;
    }

    /**
     * RecordActivityへ渡すUI参照を作成します。
     * @param settingsButton 右上の歯車Button。例: {@code gearButton}
     * @return 録音controller用参照。例: {@code WhisperRecordControls}
     */
    @NonNull
    public WhisperRecordControls controls(@NonNull final Button settingsButton) {
        return new WhisperRecordControls(
                recordButton, null, settingsButton, sourceSpinner, null, null,
                latestText, statusText, benchmarkText);
    }

    /**
     * 保存済みクイック設定を入力欄へ反映します。
     * @param windowMs 推論窓ms。例: {@code 5000}
     * @param vadThreshold VAD閾値。例: {@code 0.6f}
     * @param recordAndRetranscribe 録音保存と再推論を行うならtrue。例: {@code true}
     */
    public void bindSettings(
            final int windowMs,
            final float vadThreshold,
            final boolean recordAndRetranscribe
    ) {
        windowEdit.setText(String.valueOf(windowMs));
        vadThresholdEdit.setText(String.valueOf(vadThreshold));
        recordAndRetranscribeSwitch.setChecked(recordAndRetranscribe);
    }

    /** @param event 推論開始イベント。例: {@code WhisperProgressEvent} */
    public void showProgress(@NonNull final WhisperProgressEvent event) {
        progressBar.setVisibility(View.VISIBLE);
        progressText.setText(switch (event.phase()) {
            case FILE_READING -> "音声データを読み出し中";
            case FILE_TRANSCRIBING -> "音声データを文字起こし中";
            case REALTIME_INFERENCE -> "推論区間: " + event.startMs() + "ms ～ "
                    + (event.startMs() + event.durationMs()) + "ms";
        });
    }

    /** @param event 完了した推論。例: {@code WhisperTranscriptionEvent} */
    public void showCompleted(@NonNull final WhisperTranscriptionEvent event) {
        progressBar.setVisibility(View.GONE);
        progressText.setText(event.hasError() ? "処理に失敗しました" : "推論完了");
    }

    /** @param hint 例: {@code "5000"} @param decimal 小数ならtrue @return EditText */
    @NonNull private EditText numberEdit(final String hint, final boolean decimal) {
        final EditText edit = new EditText(activity);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER
                | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        return edit;
    }

    /** @param value 例: {@code "進捗"} @return 見出しTextView */
    @NonNull private TextView section(final String value) {
        final TextView view = text(value, 17);
        view.setPadding(0, dp(14), 0, dp(4));
        return view;
    }
    /** @param value 例: {@code "録音対象"} @return ラベルTextView */
    @NonNull private TextView label(final String value) { return text(value, 14); }
    /** @param value 例: {@code "待機中"} @param sp 例: {@code 14} @return TextView */
    @NonNull private TextView text(final String value, final int sp) {
        final TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        return view;
    }
    /** @return MATCH/WRAPのLayoutParams。例: {@code params} */
    @NonNull private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    /** @return 残り高さを使うLayoutParams。例: {@code params} */
    @NonNull private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(-1, 0, 1f); }
    /** @param value dp。例: {@code 16} @return px。例: {@code 48} */
    private int dp(final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
