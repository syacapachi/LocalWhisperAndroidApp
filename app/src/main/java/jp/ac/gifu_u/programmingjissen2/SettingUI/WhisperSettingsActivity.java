package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.tabs.TabLayout;

import org.jetbrains.annotations.Contract;

import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperLanguageOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperCppModelOption;
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
    private SwitchCompat translateSwitch;
    private EditText promptEdit;
    private Spinner fileModelSpinner;
    private Spinner fileLanguageSpinner;
    private EditText fileMaxThreadsEdit;
    private SwitchCompat fileVadSwitch;
    private EditText fileVadThresholdEdit;
    private SwitchCompat fileTranslateSwitch;
    private EditText filePromptEdit;
    private SwitchCompat audioRecordingSwitch;
    private SwitchCompat autoRetranscribeSwitch;
    private TextView statsText;
    private TextView fileStatsText;

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
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final TabLayout tabs = new TabLayout(this);
        tabs.addTab(tabs.newTab().setText("リアルタイム文字起こし設定"));
        tabs.addTab(tabs.newTab().setText("ファイル一括文字起こし設定"));
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        root.addView(tabs, fullWidthParams());

        final FrameLayout pages = new FrameLayout(this);
        final ScrollView realtimePage = createRealtimeSettingsPage();
        final ScrollView filePage = createFileSettingsPage();
        filePage.setVisibility(View.GONE);
        pages.addView(realtimePage, framePageParams());
        pages.addView(filePage, framePageParams());
        root.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                final boolean realtime = tab.getPosition() == 0;
                realtimePage.setVisibility(realtime ? View.VISIBLE : View.GONE);
                filePage.setVisibility(realtime ? View.GONE : View.VISIBLE);
            }

            @Override public void onTabUnselected(@NonNull final TabLayout.Tab tab) { }
            @Override public void onTabReselected(@NonNull final TabLayout.Tab tab) { }
        });

        final LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(dp(20), dp(8), dp(20), 0);
        root.addView(buttonRow, fullWidthParams());

        final Button saveButton = new Button(this);
        saveButton.setText("両方を保存");
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
        final LinearLayout.LayoutParams closeParams = fullWidthParams();
        closeParams.setMargins(dp(20), 0, dp(20), dp(8));
        root.addView(closeButton, closeParams);
        return root;
    }

    /**
     * CTranslate2用のリアルタイム設定ページを作成します。
     * @return 設定入力欄を含むScrollView。例: {@code ScrollView}
     */
    @NonNull
    private ScrollView createRealtimeSettingsPage() {
        final LinearLayout page = settingsPage("リアルタイム文字起こし設定",
                "録音中の短い音声窓をCTranslate2で推論する設定です。");
        page.addView(sectionText("CTranslate2モデル"));
        modelSpinner = new Spinner(this);
        final ArrayAdapter<WhisperModelOption> modelAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                WhisperModelOption.values()
        );
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        page.addView(modelSpinner, fullWidthParams());

        page.addView(sectionText("推論"));
        languageSpinner = addLanguageSpinnerRow(page);
        windowEdit = addEditRow(page, "推論窓 ms", "例: 5000", InputType.TYPE_CLASS_NUMBER);
        overlapEdit = addEditRow(page, "重なり ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        minFinalEdit = addEditRow(page, "停止時の最小 ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        maxThreadsEdit = addEditRow(page, "最大スレッド数", "1-8", InputType.TYPE_CLASS_NUMBER);

        vadSwitch = addSwitchRow(page, "CTranslate2 VAD（無音除外）を使う",
                "no-speech確率で無音窓の結果を除外します。", true);
        vadThresholdEdit = addEditRow(page, "無音確率閾値", "0.0-1.0（例: 0.6）",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        page.addView(sectionText("翻訳とプロンプト"));
        translateSwitch = addSwitchRow(page, "英語へ翻訳する",
                "入力言語を自動検出し、Whisperが対応する英語へ翻訳します。", false);
        page.addView(labelText("リアルタイム用プロンプト"));
        promptEdit = new EditText(this);
        promptEdit.setHint("例: 専門用語: CTranslate2、岐阜大学");
        promptEdit.setSingleLine(false);
        promptEdit.setMinLines(3);
        promptEdit.setGravity(android.view.Gravity.TOP);
        promptEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        page.addView(promptEdit, fullWidthParams());
        page.addView(descriptionText(
                "最大224トークン（日本語は目安150～300文字）。各推論では直前結果の末尾100文字も文脈として自動追加します。"
        ));
        page.addView(sectionText("録音と再推論"));
        audioRecordingSwitch = addSwitchRow(page, "録音する",
                "録音ごとに16kHz・モノラルのWAVファイルをアプリ内へ保存します。", false);
        autoRetranscribeSwitch = addSwitchRow(page, "あとで再推論する",
                "録音終了後、保存音声全体をWhisper.cppで一括再推論します。", false);
        audioRecordingSwitch.setOnCheckedChangeListener((button, checked) -> {
            autoRetranscribeSwitch.setEnabled(checked);
            if (!checked) {
                autoRetranscribeSwitch.setChecked(false);
            }
        });
        page.addView(sectionText("処理時間"));
        statsText = descriptionText("");
        page.addView(statsText);
        return wrapPage(page);
    }

    /**
     * Whisper.cpp用のファイル一括設定ページを作成します。
     * @return 設定入力欄を含むScrollView。例: {@code ScrollView}
     */
    @NonNull
    private ScrollView createFileSettingsPage() {
        final LinearLayout page = settingsPage("ファイル一括文字起こし設定",
                "音声・動画ファイルと録音全体をWhisper.cppで一括推論する設定です。");
        page.addView(sectionText("Whisper.cpp量子化モデル"));
        fileModelSpinner = new Spinner(this);
        final ArrayAdapter<WhisperCppModelOption> modelAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, WhisperCppModelOption.values());
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fileModelSpinner.setAdapter(modelAdapter);
        page.addView(fileModelSpinner, fullWidthParams());

        page.addView(sectionText("推論"));
        fileLanguageSpinner = addLanguageSpinnerRow(page);
        fileMaxThreadsEdit = addEditRow(page, "最大スレッド数", "1-8",
                InputType.TYPE_CLASS_NUMBER);
        fileVadSwitch = addSwitchRow(page, "Silero VAD（無音除外）を使う",
                "発話区間だけをWhisper.cppへ渡し、無音時のハルシネーションを抑えます。", true);
        fileVadThresholdEdit = addEditRow(page, "Silero VAD 発話確率閾値",
                "0.0-1.0（例: 0.5）",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        page.addView(descriptionText(
                "音声が欠落する場合は値を下げ、無音の誤認識が多い場合は値を上げます。"));

        page.addView(sectionText("翻訳とプロンプト"));
        fileTranslateSwitch = addSwitchRow(page, "英語へ翻訳する",
                "ファイルの入力言語を検出し、英語へ翻訳します。", false);
        page.addView(labelText("ファイル一括用プロンプト"));
        filePromptEdit = new EditText(this);
        filePromptEdit.setHint("例: 会議名、参加者名、専門用語");
        filePromptEdit.setSingleLine(false);
        filePromptEdit.setMinLines(3);
        filePromptEdit.setGravity(android.view.Gravity.TOP);
        filePromptEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(300)});
        page.addView(filePromptEdit, fullWidthParams());
        page.addView(descriptionText("最大224トークン（日本語は目安150～300文字）。"));

        page.addView(sectionText("処理時間"));
        fileStatsText = descriptionText("");
        page.addView(fileStatsText);

        return wrapPage(page);
    }

    /**
     * タブ内の共通レイアウトを作成します。
     * @param title ページ見出し。例: {@code "リアルタイム文字起こし設定"}
     * @param description 説明。例: {@code "CTranslate2で推論します。"}
     * @return paddingを設定した縦向きレイアウト。例: {@code LinearLayout}
     */
    @NonNull
    private LinearLayout settingsPage(final String title, final String description) {
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(16), dp(20), dp(20));
        page.addView(titleText(title));
        page.addView(descriptionText(description));
        return page;
    }

    /**
     * 設定ページをスクロール可能にします。
     * @param page 縦向きページ。例: {@code settingsPage("設定", "説明")}
     * @return ページを含むScrollView。例: {@code ScrollView}
     */
    @NonNull
    private ScrollView wrapPage(@NonNull final LinearLayout page) {
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(page, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /**
     * タブページをFrameLayout全面へ配置するパラメータを返します。
     * @return MATCH_PARENT指定。例: {@code FrameLayout.LayoutParams}
     */
    @NonNull
    private FrameLayout.LayoutParams framePageParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
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
        translateSwitch.setChecked(settings.translateToEnglish());
        promptEdit.setText(settings.prompt());
        audioRecordingSwitch.setChecked(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setEnabled(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setChecked(settings.autoRetranscribeEnabled());
        final FileTranscriptionSettings file = settings.fileTranscription();
        fileModelSpinner.setSelection(file.model().ordinal());
        fileLanguageSpinner.setSelection(
                WhisperLanguageOption.fromValue(file.language()).ordinal());
        fileMaxThreadsEdit.setText(String.valueOf(file.maxThreads()));
        fileVadSwitch.setChecked(file.vadEnabled());
        fileVadThresholdEdit.setText(String.valueOf(file.vadThreshold()));
        fileTranslateSwitch.setChecked(file.translateToEnglish());
        filePromptEdit.setText(file.prompt());
    }

    /**
     * Whisper 設定を保存します。
     * 内部でUIの更新も行います。
     */
    private void saveSettings() {
        final WhisperModelOption model = (WhisperModelOption) modelSpinner.getSelectedItem();
        final WhisperLanguageOption language = (WhisperLanguageOption) languageSpinner.getSelectedItem();
        final WhisperCppModelOption fileModel =
                (WhisperCppModelOption) fileModelSpinner.getSelectedItem();
        final WhisperLanguageOption fileLanguage =
                (WhisperLanguageOption) fileLanguageSpinner.getSelectedItem();
        final FileTranscriptionSettings fileSettings = new FileTranscriptionSettings(
                fileModel,
                fileLanguage.value(),
                parseInt(fileMaxThreadsEdit, WhisperSettings.DEFAULT_MAX_THREADS),
                false,
                fileVadSwitch.isChecked(),
                parseFloat(fileVadThresholdEdit, FileTranscriptionSettings.DEFAULT_VAD_THRESHOLD),
                fileTranslateSwitch.isChecked(),
                filePromptEdit.getText().toString()
        );

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
                fileSettings.vadThreshold(),
                fileSettings
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

        final StringBuilder fileBuilder = new StringBuilder();
        for (WhisperCppModelOption model : WhisperCppModelOption.values()) {
            if (fileBuilder.length() > 0) {
                fileBuilder.append('\n');
            }
            fileBuilder.append(formatStats(model.toString(), store.loadStats(model)));
        }
        fileStatsText.setText(fileBuilder.toString());
    }

    /**
     * 推論時間統計をフォーマットします。
     * @param model 推論モデル
     * @param stats 統計情報
     * @return フォーマットした文字列
     */
    @NonNull
    private String formatStats(final WhisperModelOption model, @NonNull final WhisperInferenceStats stats) {
        return formatStats(model.displayName(), stats);
    }

    /**
     * モデル表示名と統計を一行へ整形します。
     * @param displayName モデル名。例: {@code "Whisper.cpp small・Q8_0"}
     * @param stats 推論統計。例: {@code store.loadStats(model)}
     * @return 表示文。例: {@code "モデル: 最新 4000ms / 平均 4200ms ..."}
     */
    @NonNull
    private String formatStats(
            final String displayName,
            @NonNull final WhisperInferenceStats stats
    ) {
        if (!stats.hasSamples()) {
            return StringBufferBuilderPool.Join("", displayName, ": まだ計測なし");
        }

        return StringBufferBuilderPool.Join(
                "",
                displayName,
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
