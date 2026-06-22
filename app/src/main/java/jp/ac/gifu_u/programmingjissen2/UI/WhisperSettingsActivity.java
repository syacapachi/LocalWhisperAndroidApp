package jp.ac.gifu_u.programmingjissen2.UI;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import Utils.StringPool.StringBufferBuilderPool;

/** Whisper 関連のユーザー設定画面です。 */
public class WhisperSettingsActivity extends AppCompatActivity {
    private WhisperSettingsStore store;

    private RadioGroup modelGroup;
    private int baseRadioId;
    private int smallRadioId;
    private EditText languageEdit;
    private EditText windowEdit;
    private EditText overlapEdit;
    private EditText minFinalEdit;
    private EditText maxThreadsEdit;
    private Switch noContextSwitch;
    private Switch timestampSwitch;
    private TextView statsText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new WhisperSettingsStore(this);
        setTitle("Whisper 設定");
        setContentView(createContentView());
        bindSettings(store.load());
        refreshStats();
    }

    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = titleText("Whisper 設定");
        root.addView(title);
        root.addView(descriptionText("録音中の文字起こしに使うモデルと推論パラメータを変更します。"));

        root.addView(sectionText("モデル"));
        modelGroup = new RadioGroup(this);
        modelGroup.setOrientation(RadioGroup.VERTICAL);
        baseRadioId = View.generateViewId();
        smallRadioId = View.generateViewId();
        modelGroup.addView(modelRadioButton(baseRadioId, WhisperModelOption.BASE));
        modelGroup.addView(modelRadioButton(smallRadioId, WhisperModelOption.SMALL));
        root.addView(modelGroup);

        root.addView(sectionText("推論"));
        languageEdit = addEditRow(root, "言語", "ja / en / auto など", InputType.TYPE_CLASS_TEXT);
        windowEdit = addEditRow(root, "推論窓 ms", "例: 5000", InputType.TYPE_CLASS_NUMBER);
        overlapEdit = addEditRow(root, "重なり ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        minFinalEdit = addEditRow(root, "停止時の最小 ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        maxThreadsEdit = addEditRow(root, "最大スレッド数", "1-8", InputType.TYPE_CLASS_NUMBER);

        noContextSwitch = addSwitchRow(root, "前回文脈を使わない", "リアルタイム推論では安定しやすい設定です。", true);
        timestampSwitch = addSwitchRow(root, "Whisper 内部タイムスタンプ出力", "通常はオフのままで十分です。", false);

        root.addView(sectionText("処理時間"));
        statsText = descriptionText("");
        root.addView(statsText);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(16), 0, 0);
        root.addView(buttonRow);

        Button saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setOnClickListener((view) -> saveSettings());
        buttonRow.addView(saveButton, weightedButtonParams());

        Button resetStatsButton = new Button(this);
        resetStatsButton.setText("統計リセット");
        resetStatsButton.setOnClickListener((view) -> {
            store.resetStats();
            refreshStats();
            Toast.makeText(this, "推論時間統計をリセットしました", Toast.LENGTH_SHORT).show();
        });
        buttonRow.addView(resetStatsButton, weightedButtonParams());

        Button closeButton = new Button(this);
        closeButton.setText("閉じる");
        closeButton.setOnClickListener((view) -> finish());
        root.addView(closeButton, fullWidthParams());

        return scrollView;
    }

    private RadioButton modelRadioButton(int id, WhisperModelOption option) {
        RadioButton button = new RadioButton(this);
        button.setId(id);
        button.setText(StringBufferBuilderPool.Join(
                "",
                option.displayName(),
                " - ",
                option.description()
        ));
        button.setMinHeight(dp(48));
        return button;
    }

    private EditText addEditRow(LinearLayout root, String label, String hint, int inputType) {
        TextView labelView = labelText(label);
        root.addView(labelView);

        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        root.addView(editText, fullWidthParams());
        return editText;
    }

    private Switch addSwitchRow(
            LinearLayout root,
            String label,
            String description,
            boolean checked
    ) {
        Switch switchView = new Switch(this);
        switchView.setText(label);
        switchView.setChecked(checked);
        switchView.setMinHeight(dp(48));
        root.addView(switchView, fullWidthParams());
        root.addView(descriptionText(description));
        return switchView;
    }

    private void bindSettings(WhisperSettings settings) {
        modelGroup.check(settings.model() == WhisperModelOption.SMALL ? smallRadioId : baseRadioId);
        languageEdit.setText(settings.language());
        windowEdit.setText(String.valueOf(settings.windowMs()));
        overlapEdit.setText(String.valueOf(settings.overlapMs()));
        minFinalEdit.setText(String.valueOf(settings.minFinalMs()));
        maxThreadsEdit.setText(String.valueOf(settings.maxThreads()));
        noContextSwitch.setChecked(settings.noContext());
        timestampSwitch.setChecked(settings.printTimestamps());
    }

    private void saveSettings() {
        WhisperModelOption model = modelGroup.getCheckedRadioButtonId() == smallRadioId
                ? WhisperModelOption.SMALL
                : WhisperModelOption.BASE;

        WhisperSettings settings = new WhisperSettings(
                model,
                languageEdit.getText().toString(),
                parseInt(windowEdit, WhisperSettings.DEFAULT_WINDOW_MS),
                parseInt(overlapEdit, WhisperSettings.DEFAULT_OVERLAP_MS),
                parseInt(minFinalEdit, WhisperSettings.DEFAULT_MIN_FINAL_MS),
                parseInt(maxThreadsEdit, WhisperSettings.DEFAULT_MAX_THREADS),
                noContextSwitch.isChecked(),
                timestampSwitch.isChecked()
        );
        store.save(settings);
        bindSettings(settings);
        refreshStats();
        Toast.makeText(this, "Whisper 設定を保存しました", Toast.LENGTH_SHORT).show();
    }

    private void refreshStats() {
        WhisperInferenceStats baseStats = store.loadStats(WhisperModelOption.BASE);
        WhisperInferenceStats smallStats = store.loadStats(WhisperModelOption.SMALL);
        statsText.setText(StringBufferBuilderPool.Join(
                "",
                formatStats(WhisperModelOption.BASE, baseStats),
                "\n",
                formatStats(WhisperModelOption.SMALL, smallStats),
                "\n録音画面でも推論 1 回ごとの処理時間を確認できます。"
        ));
    }

    private String formatStats(WhisperModelOption model, WhisperInferenceStats stats) {
        if (!stats.hasSamples()) {
            return StringBufferBuilderPool.Join("", model.displayName(), ": まだ計測なし");
        }

        return StringBufferBuilderPool.Join(
                "",
                model.displayName(),
                ": 最新 ",
                stats.lastMs(),
                "ms / 平均 ",
                stats.averageMs(),
                "ms / 最小 ",
                stats.minMs(),
                "ms / 最大 ",
                stats.maxMs(),
                "ms / ",
                stats.count(),
                "回"
        );
    }

    private int parseInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private TextView titleText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView sectionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private TextView labelText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    private TextView descriptionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
