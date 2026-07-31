package jp.ac.gifu_u.programmingjissen2.MainUI;

import android.app.Activity;
import android.graphics.Color;
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
import jp.ac.gifu_u.programmingjissen2.SettingUI.SeekEditControl;

/** 録音開始に必要な操作と進捗、最新結果を一画面へまとめたタブです。 */
public final class QuickStartTabView {
    private final Activity activity;
    private final LinearLayout root;
    private final Button fileButton;
    private final Button recordButton;
    private final Button recordingPauseButton;
    private final Button inferenceButton;
    private final Spinner sourceSpinner;
    private final SeekEditControl windowControl;
    private final SeekEditControl vadThresholdControl;
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
        windowControl = SeekEditControl.add(
                activity, content, "推論窓（秒）", 1.0, 30.0, 1.0);
        vadThresholdControl = SeekEditControl.add(
                activity, content, "Silero VAD 発話確率閾値", 0.0, 1.0, 0.1);
        recordAndRetranscribeSwitch = new SwitchCompat(activity);
        recordAndRetranscribeSwitch.setText("録音してあとで再推論");
        recordAndRetranscribeSwitch.setMinHeight(dp(48));
        content.addView(recordAndRetranscribeSwitch, matchWrap());

        statusText = text("状態: 待機中", 13);
        content.addView(statusText, matchWrap());

        final LinearLayout controlRow = new LinearLayout(activity);
        controlRow.setGravity(Gravity.CENTER);

        recordingPauseButton = controlButton("⏸", Color.BLACK, "録音を一時停止");
        recordingPauseButton.setVisibility(View.GONE);
        controlRow.addView(recordingPauseButton, controlButtonParams());

        recordButton = controlButton("●", Color.RED, "録音セッションを開始");
        controlRow.addView(recordButton, controlButtonParams());

        inferenceButton = controlButton("🅐", Color.BLACK, "推論を一時停止");
        inferenceButton.setVisibility(View.GONE);
        controlRow.addView(inferenceButton, controlButtonParams());
        content.addView(controlRow, matchWrap());

        content.addView(section("モデル別推論時間（デバッグ）"));
        benchmarkText = text("推論時間 未計測", 12);
        content.addView(benchmarkText, matchWrap());
    }

    /** @return タブへ追加するルートView。例: {@code LinearLayout} */
    @NonNull public View view() { return root; }

    /** @return ファイル選択を開始するButton。例: {@code fileButton} */
    @NonNull public Button fileButton() { return fileButton; }
    /** @return 推論窓入力欄。例: 内容{@code "5"} */
    @NonNull public EditText windowEdit() { return windowControl.editText(); }
    /** @return VAD閾値入力欄。例: 内容{@code "0.6"} */
    @NonNull public EditText vadThresholdEdit() { return vadThresholdControl.editText(); }
    /** @return 推論窓のSeekBar＋数値入力。例: {@code windowControl} */
    @NonNull public SeekEditControl windowControl() { return windowControl; }
    /** @return VAD閾値のSeekBar＋数値入力。例: {@code vadThresholdControl} */
    @NonNull public SeekEditControl vadThresholdControl() { return vadThresholdControl; }
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
                recordButton, recordingPauseButton, inferenceButton, settingsButton,
                sourceSpinner,
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
        windowControl.setValue(windowMs / 1000.0);
        vadThresholdControl.setValue(vadThreshold);
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
    /**
     * 録音操作行へ置く記号ボタンを作成します。
     * @param glyph 表示記号。例: {@code "●"}
     * @param color 文字色。例: {@code Color.RED}
     * @param description 読み上げ用説明。例: {@code "録音セッションを開始"}
     * @return 56×48dp枠へ収まる小型Button。例: {@code Button}
     */
    @NonNull
    private Button controlButton(
            @NonNull final String glyph,
            final int color,
            @NonNull final String description
    ) {
        final Button button = new Button(activity);
        button.setText(glyph);
        button.setTextColor(color);
        button.setContentDescription(description);
        button.setTextSize(20);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    /** @return 中央に並べる56×48dpのLayoutParams。例: {@code width=56dp} */
    @NonNull
    private LinearLayout.LayoutParams controlButtonParams() {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(48));
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }
    /** @param value dp。例: {@code 16} @return px。例: {@code 48} */
    private int dp(final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
