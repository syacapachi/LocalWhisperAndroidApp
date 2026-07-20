package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.jetbrains.annotations.Contract;

import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperLanguageOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** Whisper 関連のユーザー設定画面です。 */
public class WhisperSettingsActivity extends AppCompatActivity {
    private WhisperSettingsStore store;

    private Spinner modelSpinner;
    /**  言語選択のドロップダウン */
    private Spinner languageSpinner;
    /** 推論窓の大きさの 入力フィールド */
    private EditText windowEdit;
    /** 推論窓の重なりの 入力フィールド */
    private EditText overlapEdit;
    /** 停止時の最終数論の 入力フィールド */
    private EditText minFinalEdit;
    /** 推論に使う最大スレッド数の 入力フィールド */
    private EditText maxThreadsEdit;
    private SwitchCompat vadSwitch;
    private EditText vadThresholdEdit;
    private EditText sileroVadThresholdEdit;
    private SwitchCompat translateSwitch;
    private EditText promptEdit;
    private SwitchCompat audioRecordingSwitch;
    private SwitchCompat autoRetranscribeSwitch;
    private TextView statsText;

    @Override
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new WhisperSettingsStore(this);
        setTitle("音声認識設定");
        setContentView(createContentView());
        bindSettings(store.load());
        refreshStats();
    }

    /**
     * 設定画面のビューを生成します。
     * @return 設定画面のビュー
     */
    @NonNull
    private View createContentView() {
        // スクロールできる画面を生成。
        final ScrollView scrollView = new ScrollView(this);
        // 画面の中にレイアウトの元
        final LinearLayout root = new LinearLayout(this);
        // 縦に伸びる
        root.setOrientation(LinearLayout.VERTICAL);
        // Padding設定
        final int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        // タイトル
        final TextView title = titleText("音声認識設定");
        root.addView(title);
        root.addView(descriptionText("録音中の文字起こしに使うモデルと推論パラメータを変更します。"));

        // モデルassetsパス選択のドロップダウン
        root.addView(sectionText("モデル"));
        modelSpinner = new Spinner(this);
        final ArrayAdapter<WhisperModelOption> modelAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                WhisperModelOption.values()
        );
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        root.addView(modelSpinner, fullWidthParams());
        root.addView(descriptionText(
                "モデル選択はリアルタイム推論で使うCTranslate2モデルへ適用されます。"
        ));
        root.addView(descriptionText(
                "録音中は選択したCTranslate2モデル、音声ファイルと録音全体の再推論は対応するWhisper.cppモデルを使います。"
        ));

        // 設定の入力フィールド
        root.addView(sectionText("推論"));
        languageSpinner = addLanguageSpinnerRow(root);
        windowEdit = addEditRow(root, "推論窓 ms", "例: 5000", InputType.TYPE_CLASS_NUMBER);
        overlapEdit = addEditRow(root, "重なり ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        minFinalEdit = addEditRow(root, "停止時の最小 ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        maxThreadsEdit = addEditRow(root, "最大スレッド数", "1-8", InputType.TYPE_CLASS_NUMBER);

        vadSwitch = addSwitchRow(root, "VAD（無音除外）を使う",
                "リアルタイム推論ではCTranslate2、ファイル・録音全体の推論ではSilero VADを使います。", true);
        vadThresholdEdit = addEditRow(root, "CTranslate2 無音確率閾値", "0.0-1.0（例: 0.6）",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sileroVadThresholdEdit = addEditRow(root, "Silero VAD 発話確率閾値", "0.0-1.0（例: 0.5）",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(descriptionText(
                "Sileroは確率が閾値以上の区間を発話として残します。後半が欠落する場合は値を下げてください。"
        ));

        root.addView(sectionText("翻訳とプロンプト"));
        translateSwitch = addSwitchRow(root, "英語へ翻訳する",
                "入力言語を自動検出し、Whisperが対応する英語へ翻訳します。", false);
        root.addView(labelText("プロンプト"));
        promptEdit = new EditText(this);
        promptEdit.setHint("例: 専門用語: CTranslate2、岐阜大学");
        promptEdit.setSingleLine(false);
        promptEdit.setMinLines(3);
        promptEdit.setGravity(android.view.Gravity.TOP);
        promptEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        root.addView(promptEdit, fullWidthParams());
        root.addView(descriptionText(
                "最大224トークン（日本語は目安150～300文字）。各推論では直前結果の末尾100文字も文脈として自動追加します。"
        ));

        root.addView(sectionText("音声記録"));
        audioRecordingSwitch = addSwitchRow(root, "音声記録を有効にする",
                "録音ごとに16kHz・モノラルのWAVファイルをアプリ内へ保存します。", false);
        autoRetranscribeSwitch = addSwitchRow(root, "録音終了時に自動で再推論する",
                "リアルタイム推論の終了後、保存音声全体を専用スレッドで再推論します。", false);
        audioRecordingSwitch.setOnCheckedChangeListener((button, checked) -> {
            autoRetranscribeSwitch.setEnabled(checked);
            if (!checked) {
                autoRetranscribeSwitch.setChecked(false);
            }
        });

        // モデルの推論統計を出す
        root.addView(sectionText("処理時間"));
        statsText = descriptionText("");
        root.addView(statsText);

        // ボタンを表示
        final LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(16), 0, 0);
        root.addView(buttonRow);

        final Button saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setOnClickListener((view) -> saveSettings());
        buttonRow.addView(saveButton, weightedButtonParams());

        final Button resetStatsButton = new Button(this);
        resetStatsButton.setText("統計リセット");
        resetStatsButton.setOnClickListener((view) -> {
            store.resetStats();
            refreshStats();
            Toast.makeText(this, "推論時間統計をリセットしました", Toast.LENGTH_SHORT).show();
        });
        buttonRow.addView(resetStatsButton, weightedButtonParams());

        final Button closeButton = new Button(this);
        closeButton.setText("閉じる");
        closeButton.setOnClickListener((view) -> finish());
        root.addView(closeButton, fullWidthParams());

        return scrollView;
    }

    /**
     * 言語選択のドロップダウンを生成します。
     * @param root 親のレイアウト
     * @return 言語選択のドロップダウンコンテンツ
     */
    @NonNull
    private Spinner addLanguageSpinnerRow(@NonNull final LinearLayout root) {
        root.addView(labelText("言語"));

        // ドロップダウン
        final Spinner spinner = new Spinner(this);
        // 選択肢を生成。
        final ArrayAdapter<WhisperLanguageOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                WhisperLanguageOption.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        root.addView(spinner, fullWidthParams());
        return spinner;
    }

    /**
     * 編集できるテキストフィールドを生成
     * @param root 親のレイアウト
     * @param label 表示するテキスト
     * @param hint ヒント
     * @param inputType 入力タイプ
     * @return 編集できるテキストフィールドコンテンツ
     */
    @NonNull
    private EditText addEditRow(
            @NonNull final LinearLayout root,
            final String label,
            final String hint,
            final int inputType
    ) {
        final TextView labelView = labelText(label);
        root.addView(labelView);

        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setInputType(inputType);
        root.addView(editText, fullWidthParams());
        return editText;
    }

    @NonNull
    private SwitchCompat addSwitchRow(
            @NonNull final LinearLayout root,
            final String label,
            final String description,
            boolean checked
    ) {
        final SwitchCompat switchView = new SwitchCompat(this);
        switchView.setText(label);
        switchView.setChecked(checked);
        switchView.setMinHeight(dp(48));
        root.addView(switchView, fullWidthParams());
        root.addView(descriptionText(description));
        return switchView;
    }

    /**
     * Whisper 設定を画面に反映します。
     * @param settings 表示する設定
     */
    private void bindSettings(@NonNull final WhisperSettings settings) {
        modelSpinner.setSelection(settings.model().ordinal());
        languageSpinner.setSelection(WhisperLanguageOption.fromValue(settings.language()).ordinal());
        windowEdit.setText(String.valueOf(settings.windowMs()));
        overlapEdit.setText(String.valueOf(settings.overlapMs()));
        minFinalEdit.setText(String.valueOf(settings.minFinalMs()));
        maxThreadsEdit.setText(String.valueOf(settings.maxThreads()));
        vadSwitch.setChecked(settings.vadEnabled());
        vadThresholdEdit.setText(String.valueOf(settings.vadThreshold()));
        sileroVadThresholdEdit.setText(String.valueOf(settings.sileroVadThreshold()));
        translateSwitch.setChecked(settings.translateToEnglish());
        promptEdit.setText(settings.prompt());
        audioRecordingSwitch.setChecked(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setEnabled(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setChecked(settings.autoRetranscribeEnabled());
    }

    /**
     * Whisper 設定を保存します。
     * 内部でUIの更新も行います。
     */
    private void saveSettings() {
        final WhisperModelOption model = (WhisperModelOption) modelSpinner.getSelectedItem();
        final WhisperLanguageOption language = (WhisperLanguageOption) languageSpinner.getSelectedItem();

        WhisperSettings settings = new WhisperSettings(
                model,
                language.value(),
                parseInt(windowEdit, WhisperSettings.DEFAULT_WINDOW_MS),
                parseInt(overlapEdit, WhisperSettings.DEFAULT_OVERLAP_MS),
                parseInt(minFinalEdit, WhisperSettings.DEFAULT_MIN_FINAL_MS),
                parseInt(maxThreadsEdit, WhisperSettings.DEFAULT_MAX_THREADS),
                false,
                false,
                false,
                audioRecordingSwitch.isChecked(),
                autoRetranscribeSwitch.isChecked(),
                vadSwitch.isChecked(),
                parseFloat(vadThresholdEdit, WhisperSettings.DEFAULT_VAD_THRESHOLD),
                translateSwitch.isChecked(),
                promptEdit.getText().toString(),
                parseFloat(
                        sileroVadThresholdEdit,
                        WhisperSettings.DEFAULT_SILERO_VAD_THRESHOLD
                )
        );
        store.save(settings);
        bindSettings(settings);
        refreshStats();
        Toast.makeText(this, "音声認識設定を保存しました", Toast.LENGTH_SHORT).show();
    }

    /**
     * WhisperSettingStoreから統計情報を取得し、テキストに表示します。
     */
    private void refreshStats() {
        final StringBuilder builder = new StringBuilder();
        for (WhisperModelOption model : WhisperModelOption.values()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(formatStats(model, store.loadStats(model)));
        }
        builder.append("\n録音画面でも推論 1 回ごとの処理時間を確認できます。");
        statsText.setText(builder.toString());
    }

    /**
     * 推論時間統計をフォーマットします。
     * @param model 推論モデル
     * @param stats 統計情報
     * @return フォーマットした文字列
     */
    @NonNull
    private String formatStats(final WhisperModelOption model, @NonNull final WhisperInferenceStats stats) {
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

    /**
     * テキストから数字に変換します。
     * @param editText テキストフィールド
     * @param fallback 変換できない場合のフォールバック値
     * @return 変換した値
     */
    private int parseInt(@NonNull final EditText editText, final int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 入力欄をfloatへ変換します。
     * @param editText 入力欄。例: 内容が{@code "0.6"}の欄
     * @param fallback 変換失敗時の値。例: {@code 0.6f}
     * @return 変換値。例: {@code 0.6f}。例外は外へ送出しません
     */
    private float parseFloat(@NonNull final EditText editText, final float fallback) {
        try {
            return Float.parseFloat(editText.getText().toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * タイトルを表示
     * @param text タイトルの文字
     * @return 表示した文字のコンテンツ
     */
    @NonNull
    private TextView titleText(final String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    /**
     * 区切られた文字を表示
     * @param text 表示する文字列
     * @return 文字を表示したコンテンツ
     */
    @NonNull
    private TextView sectionText(final String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    /**
     * ラベルを表示
     * @param text 表示するラベルテキスト
     * @return テキストを表示したコンテンツ
     */
    @NonNull
    private TextView labelText(final String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(0, dp(10), 0, 0);
        return view;
    }

    /**
     * 説明テキストを表示
     * @param text 表示するテキスト
     * @return 表示したコンテンツ
     */
    @NonNull
    private TextView descriptionText(final String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    /**
     * 全体の幅を取る
     * @return 全体の幅を取る
     */
    @NonNull
    @Contract(" -> new")
    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    /**
     * ボタンの幅を設定します。
     * @return ボタンの幅を設定したLayoutParams
     */
    @NonNull
    private LinearLayout.LayoutParams weightedButtonParams() {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    /**
     * 指定した深さの dp を計算します。
     * @param value 深さ
     * @return 指定した深さの dp
     */
    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
