package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.os.Bundle;
import android.net.Uri;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.tabs.TabLayout;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionWindowLimits;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperLanguageOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.Transcription.ModelLoadProbe;

/** Whisper 関連のユーザー設定画面です。 */
public class WhisperSettingsActivity extends AppCompatActivity {
    private WhisperSettingsStore store;
    private ExternalModelRepository modelRepository;
    private ITranscriptionModel[] realtimeModels;
    private ITranscriptionModel[] fileModels;
    private String pendingExternalModelName = "";
    private String pendingComputeType = "int8";
    private volatile File pendingImportedModel;
    private final ActivityResultLauncher<String[]> externalModelFilePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onExternalModelFileSelected);
    private final ActivityResultLauncher<Uri> cTranslate2DirectoryPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocumentTree(),
                    this::onCTranslate2DirectorySelected);

    private Spinner modelSpinner;
    /**  言語選択のドロップダウン */
    private Spinner languageSpinner;
    /** リアルタイム推論窓のSeekBar＋秒数入力です。 */
    private SeekEditControl windowControl;
    /** 推論窓の重なりの 入力フィールド */
    private EditText overlapEdit;
    /** 停止時の最終数論の 入力フィールド */
    private EditText minFinalEdit;
    /** 推論に使う最大スレッド数の 入力フィールド */
    private EditText maxThreadsEdit;
    private SwitchCompat vadSwitch;
    private SeekEditControl vadThresholdControl;
    private SwitchCompat translateSwitch;
    private EditText promptEdit;
    private Spinner fileModelSpinner;
    private Spinner fileLanguageSpinner;
    private EditText fileMaxThreadsEdit;
    private SeekEditControl fileWindowControl;
    private SwitchCompat fileVadSwitch;
    private SeekEditControl fileVadThresholdControl;
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
        modelRepository = new ExternalModelRepository(this);
        realtimeModels = modelRepository.list(WhisperInferenceEngine.CTRANSLATE2);
        fileModels = modelRepository.list(WhisperInferenceEngine.WHISPER_CPP);
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
        final ArrayAdapter<ITranscriptionModel> modelAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                realtimeModels
        );
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        page.addView(modelSpinner, fullWidthParams());
        page.addView(externalModelControls());

        page.addView(sectionText("推論"));
        languageSpinner = addLanguageSpinnerRow(page);
        windowControl = SeekEditControl.add(
                this, page, "推論窓（秒）", 1.0, 30.0, 1.0);
        overlapEdit = addEditRow(page, "重なり ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        minFinalEdit = addEditRow(page, "停止時の最小 ms", "例: 1000", InputType.TYPE_CLASS_NUMBER);
        maxThreadsEdit = addEditRow(page, "最大スレッド数", "1-8", InputType.TYPE_CLASS_NUMBER);

        vadSwitch = addSwitchRow(page, "Silero VAD（発話抽出）を使う",
                "推論前に発話区間だけを抽出し、CTranslate2へ渡します。", true);
        vadThresholdControl = SeekEditControl.add(
                this, page, "Silero VAD 発話確率閾値", 0.0, 1.0, 0.1);

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
        final ArrayAdapter<ITranscriptionModel> modelAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, fileModels);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fileModelSpinner.setAdapter(modelAdapter);
        page.addView(fileModelSpinner, fullWidthParams());
        page.addView(externalModelControls());

        page.addView(sectionText("推論"));
        fileLanguageSpinner = addLanguageSpinnerRow(page);
        final int fileWindowMaxSeconds = FileTranscriptionWindowLimits.maxSeconds();
        fileWindowControl = SeekEditControl.add(
                this,
                page,
                "推論窓（秒）",
                FileTranscriptionWindowLimits.MIN_SECONDS,
                fileWindowMaxSeconds,
                FileTranscriptionWindowLimits.STEP_SECONDS
        );
        page.addView(descriptionText(StringBufferBuilderPool.Join(
                "",
                "30秒から",
                fileWindowMaxSeconds,
                "秒まで。端末メモリの半分または5分を上限として10秒刻みで設定します。"
        )));
        fileMaxThreadsEdit = addEditRow(page, "最大スレッド数", "1-8",
                InputType.TYPE_CLASS_NUMBER);
        fileVadSwitch = addSwitchRow(page, "Silero VAD（無音除外）を使う",
                "発話区間だけをWhisper.cppへ渡し、無音時のハルシネーションを抑えます。", true);
        fileVadThresholdControl = SeekEditControl.add(
                this, page, "Silero VAD 発話確率閾値", 0.0, 1.0, 0.1);
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
        modelSpinner.setSelection(indexOf(realtimeModels, settings.model().key()));
        languageSpinner.setSelection(WhisperLanguageOption.fromValue(settings.language()).ordinal());
        windowControl.setValue(settings.windowMs() / 1000.0);
        overlapEdit.setText(String.valueOf(settings.overlapMs()));
        minFinalEdit.setText(String.valueOf(settings.minFinalMs()));
        maxThreadsEdit.setText(String.valueOf(settings.maxThreads()));
        vadSwitch.setChecked(settings.vadEnabled());
        vadThresholdControl.setValue(settings.vadThreshold());
        translateSwitch.setChecked(settings.translateToEnglish());
        promptEdit.setText(settings.prompt());
        audioRecordingSwitch.setChecked(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setEnabled(settings.audioRecordingEnabled());
        autoRetranscribeSwitch.setChecked(settings.autoRetranscribeEnabled());
        final FileTranscriptionSettings file = settings.fileTranscription();
        fileModelSpinner.setSelection(indexOf(fileModels, file.model().key()));
        fileLanguageSpinner.setSelection(
                WhisperLanguageOption.fromValue(file.language()).ordinal());
        fileWindowControl.setValue(file.windowMs() / 1000.0);
        fileMaxThreadsEdit.setText(String.valueOf(file.maxThreads()));
        fileVadSwitch.setChecked(file.vadEnabled());
        fileVadThresholdControl.setValue(file.vadThreshold());
        fileTranslateSwitch.setChecked(file.translateToEnglish());
        filePromptEdit.setText(file.prompt());
    }

    /**
     * Whisper 設定を保存します。
     * 内部でUIの更新も行います。
     */
    private void saveSettings() {
        final ITranscriptionModel model = (ITranscriptionModel) modelSpinner.getSelectedItem();
        final WhisperLanguageOption language = (WhisperLanguageOption) languageSpinner.getSelectedItem();
        final ITranscriptionModel fileModel =
                (ITranscriptionModel) fileModelSpinner.getSelectedItem();
        final WhisperLanguageOption fileLanguage =
                (WhisperLanguageOption) fileLanguageSpinner.getSelectedItem();
        final FileTranscriptionSettings fileSettings = new FileTranscriptionSettings(
                fileModel,
                fileLanguage.value(),
                parseInt(fileMaxThreadsEdit, WhisperSettings.DEFAULT_MAX_THREADS),
                (int) Math.round(fileWindowControl.valueOr(
                        FileTranscriptionSettings.DEFAULT_WINDOW_MS / 1000.0) * 1000.0),
                fileVadSwitch.isChecked(),
                (float) fileVadThresholdControl.valueOr(
                        FileTranscriptionSettings.DEFAULT_VAD_THRESHOLD),
                fileTranslateSwitch.isChecked(),
                filePromptEdit.getText().toString()
        );

        WhisperSettings settings = new WhisperSettings(
                model,
                language.value(),
                (int) Math.round(windowControl.valueOr(
                        WhisperSettings.DEFAULT_WINDOW_MS / 1000.0) * 1000.0),
                parseInt(overlapEdit, WhisperSettings.DEFAULT_OVERLAP_MS),
                parseInt(minFinalEdit, WhisperSettings.DEFAULT_MIN_FINAL_MS),
                parseInt(maxThreadsEdit, WhisperSettings.DEFAULT_MAX_THREADS),
                audioRecordingSwitch.isChecked(),
                autoRetranscribeSwitch.isChecked(),
                vadSwitch.isChecked(),
                (float) vadThresholdControl.valueOr(WhisperSettings.DEFAULT_VAD_THRESHOLD),
                translateSwitch.isChecked(),
                promptEdit.getText().toString(),
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
        for (ITranscriptionModel model : realtimeModels) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(formatStats(model, store.loadStats(model)));
        }
        builder.append("\n録音画面でも推論 1 回ごとの処理時間を確認できます。");
        statsText.setText(builder.toString());

        final StringBuilder fileBuilder = new StringBuilder();
        for (ITranscriptionModel model : fileModels) {
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
    private String formatStats(
            final ITranscriptionModel model,
            @NonNull final WhisperInferenceStats stats
    ) {
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
     * 外部モデル追加Dialogを開くボタンを作成します。
     * @return クリック時に名前・計算型と.binを選択するButton。例: {@code addButton}
     */
    @NonNull
    private Button externalModelButton() {
        final Button button = new Button(this);
        button.setText("外部モデルを追加");
        button.setOnClickListener(view -> showExternalModelDialog());
        return button;
    }

    /**
     * 外部モデル追加ボタンと開閉式の注意点を縦にまとめます。
     * @return 設定ページへ追加するコンテナ。例: {@code externalModelControls}
     */
    @NonNull
    private View externalModelControls() {
        final LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(externalModelButton(), fullWidthParams());

        final Button toggle = new Button(this);
        toggle.setText("インポートの際の注意点 ▼");
        toggle.setContentDescription("インポートの際の注意点を開く");
        container.addView(toggle, fullWidthParams());

        final TextView notes = descriptionText(
                "・モデル追加時はアプリ内部へコピーします。空き容量が不足していると失敗します。\n"
                        + "・CTranslate2では、同じディレクトリ直下にmodel.bin、"
                        + "vocabulary.json、tokenizer.json、*config.jsonが必要です。\n"
                        + "・Whisper.cppでは.bin形式のモデルファイルを選択してください。\n"
                        + "・検証中はモデルを実際に読み込むため、完了まで時間とメモリを使用します。\n"
                        + "・検証に失敗したコピーは削除されます。選択元のファイルは削除されません。\n"
                        + "・登録成功後のモデルはアプリ領域を使用し、アプリを削除すると一緒に削除されます。"
        );
        notes.setVisibility(View.GONE);
        container.addView(notes, fullWidthParams());

        toggle.setOnClickListener(view -> {
            final boolean opening = notes.getVisibility() != View.VISIBLE;
            notes.setVisibility(opening ? View.VISIBLE : View.GONE);
            toggle.setText(opening
                    ? "インポートの際の注意点 ▲"
                    : "インポートの際の注意点 ▼");
            toggle.setContentDescription(opening
                    ? "インポートの際の注意点を閉じる"
                    : "インポートの際の注意点を開く");
        });
        return container;
    }

    /** 名前とCTranslate2計算型を入力し、Activity Result APIで.bin選択を開始します。 */
    private void showExternalModelDialog() {
        final LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = addEditRow(
                fields, "名前", "例: 会議用small", InputType.TYPE_CLASS_TEXT);
        final EditText computeType = addEditRow(
                fields, "CTranslate2計算型", "例: int8", InputType.TYPE_CLASS_TEXT);
        computeType.setText("int8");
        new AlertDialog.Builder(this)
                .setTitle("外部モデルを追加")
                .setMessage(".binを選択します。CTranslate2のmodel.binの場合は、続けて同じディレクトリを選択してください。")
                .setView(fields)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("ファイルを選択", (dialog, which) ->
                        launchExternalModelPicker(
                                name.getText().toString(),
                                computeType.getText().toString()))
                .show();
    }

    /**
     * 入力値を保持して.bin用OpenDocumentを起動します。
     * @param name UI名。例: {@code "会議用small"}
     * @param computeType CTranslate2計算型。例: {@code "int8"}
     */
    private void launchExternalModelPicker(
            @NonNull final String name,
            final String computeType
    ) {
        if (name.trim().isEmpty()) {
            Toast.makeText(this, "名前を入力してください", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingExternalModelName = name.trim();
        pendingComputeType = computeType.trim().isEmpty() ? "int8" : computeType.trim();
        externalModelFilePicker.launch(new String[]{
                "application/octet-stream", "application/x-binary", "application/x-ggml", "*/*"
        });
    }

    /**
     * OpenDocumentで選択した.binを取り込み、Whisper.cppとして読み込みを試します。
     * @param uri 選択URI。キャンセル時はnull。例: {@code content://.../ggml-small.bin}
     */
    private void onExternalModelFileSelected(@Nullable final Uri uri) {
        if (uri == null) {
            clearPendingExternalModel();
            return;
        }
        Toast.makeText(this, "モデルを読み込んで検証しています", Toast.LENGTH_LONG).show();
        StringBufferBuilderPool.NewThreadWithPoolCleanup(() -> {
            try {
                final String selectedName =
                        ExternalModelImporter.displayName(getContentResolver(), uri);
                if (!selectedName.toLowerCase(Locale.ROOT).endsWith(".bin")) {
                    throw new IOException(".binファイルを選択してください");
                }
                final File imported = ExternalModelImporter.importWhisperBin(this, uri);
                replacePendingImportedModel(imported);
                final ModelLoadProbe.Result probe =
                        ModelLoadProbe.probe(imported.getAbsolutePath(), pendingComputeType);
                if (probe.whisperLoaded()) {
                    saveImportedModel(imported, probe);
                    return;
                }
                if ("model.bin".equalsIgnoreCase(selectedName)) {
                    runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                                "CTranslate2の必須ファイルを確認するため、model.binと同じディレクトリを選択してください",
                            Toast.LENGTH_LONG
                        ).show();
                        cTranslate2DirectoryPicker.launch(null);
                    });
                    return;
                }
                showProbeError(probe);
            } catch (Exception error) {
                showImportError(error);
            }
        }, "ExternalModelProbe").start();
    }

    /**
     * 選択ディレクトリのCTranslate2必須ファイルを検査・コピーして読み込みます。
     * @param uri OpenDocumentTreeのURI。キャンセル時はnull。例: {@code content://.../tree/model}
     */
    private void onCTranslate2DirectorySelected(@Nullable final Uri uri) {
        if (uri == null) {
            discardPendingExternalModel();
            return;
        }
        StringBufferBuilderPool.NewThreadWithPoolCleanup(() -> {
            try {
                final File directory =
                        ExternalModelImporter.importCTranslate2Directory(this, uri);
                replacePendingImportedModel(directory);
                final ModelLoadProbe.Result probe = ModelLoadProbe.probe(
                        directory.getAbsolutePath(), pendingComputeType);
                if (!probe.cTranslate2Loaded()) {
                    showProbeError(probe);
                    return;
                }
                saveImportedModel(directory, probe);
            } catch (Exception error) {
                showImportError(error);
            }
        }, "CTranslate2ModelImport").start();
    }

    /**
     * 検証済みの内部モデルパスをJSONへ保存し、Spinnerへ反映します。
     * @param imported コピー済みファイルまたはディレクトリ。例: {@code model.bin}
     * @param probe native読み込み結果。例: {@code ModelLoadProbe.probe(path, "int8")}
     */
    private void saveImportedModel(
            @NonNull final File imported,
            @NonNull final ModelLoadProbe.Result probe
    ) {
        try {
            final ITranscriptionModel saved = modelRepository.saveVerified(
                    pendingExternalModelName,
                    imported.getAbsolutePath(),
                    pendingComputeType,
                    probe);
            runOnUiThread(() -> {
                refreshModelChoices(saved);
                Toast.makeText(
                        this,
                        saved.label() + " を " + saved.engine().jsonValue() + " として追加しました",
                        Toast.LENGTH_LONG
                ).show();
                clearPendingExternalModel();
            });
        } catch (Exception error) {
            showImportError(error);
        }
    }

    /**
     * 両nativeエンジンの失敗内容をDialogへ表示します。
     * @param probe 失敗結果。例: {@code ModelLoadProbe.probe(path, "int8")}
     */
    private void showProbeError(@NonNull final ModelLoadProbe.Result probe) {
        final String error = "Whisper.cpp: " + probe.whisperError()
                + "\nCTranslate2: " + probe.cTranslate2Error();
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("モデルを読み込めませんでした")
                    .setMessage(error)
                    .setPositiveButton("閉じる", null)
                    .show();
            discardPendingExternalModel();
        });
    }

    /**
     * インポート例外をToastへ表示します。
     * @param error 原因。例: {@code new IOException("model.binがありません")}
     */
    private void showImportError(@NonNull final Exception error) {
        runOnUiThread(() -> {
            Toast.makeText(
                    this,
                    "外部モデルを追加できませんでした: " + error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
            discardPendingExternalModel();
        });
    }

    /**
     * 新しいインポートを追跡し、以前の一時コピーがあれば削除します。
     * @param imported 新しいコピー。例: {@code importedModel}
     */
    private void replacePendingImportedModel(@NonNull final File imported) throws IOException {
        final File previous = pendingImportedModel;
        pendingImportedModel = imported;
        if (previous != null
                && !previous.getCanonicalPath().equals(imported.getCanonicalPath())) {
            if (!ImportedModelCleanup.delete(this, previous)) {
                throw new IOException("以前の検証用モデルを削除できませんでした");
            }
        }
    }

    /** 検証失敗した一時コピーを削除し、保留情報を初期化します。 */
    private void discardPendingExternalModel() {
        final File imported = pendingImportedModel;
        pendingImportedModel = null;
        if (imported != null) {
            try {
                if (!ImportedModelCleanup.delete(this, imported)) {
                    Toast.makeText(this, "検証失敗モデルを削除できませんでした", Toast.LENGTH_LONG).show();
                }
            } catch (IOException error) {
                Toast.makeText(
                        this,
                        "検証失敗モデルを削除できませんでした: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
        clearPendingExternalModel();
    }

    /** 保存成功後の保留中モデル名・計算型・一時コピー参照を初期値へ戻します。 */
    private void clearPendingExternalModel() {
        pendingExternalModelName = "";
        pendingComputeType = "int8";
        pendingImportedModel = null;
    }

    /**
     * JSON更新後に両モデルSpinnerを再構築します。
     * @param selected 新しく選択するモデル。例: {@code externalModel}
     */
    private void refreshModelChoices(@NonNull final ITranscriptionModel selected) {
        final String realtimeKey = selected.engine() == WhisperInferenceEngine.CTRANSLATE2
                ? selected.key() : ((ITranscriptionModel) modelSpinner.getSelectedItem()).key();
        final String fileKey = selected.engine() == WhisperInferenceEngine.WHISPER_CPP
                ? selected.key() : ((ITranscriptionModel) fileModelSpinner.getSelectedItem()).key();
        realtimeModels = modelRepository.list(WhisperInferenceEngine.CTRANSLATE2);
        fileModels = modelRepository.list(WhisperInferenceEngine.WHISPER_CPP);
        final ArrayAdapter<ITranscriptionModel> realtimeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, realtimeModels);
        realtimeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(realtimeAdapter);
        modelSpinner.setSelection(indexOf(realtimeModels, realtimeKey));
        final ArrayAdapter<ITranscriptionModel> fileAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, fileModels);
        fileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fileModelSpinner.setAdapter(fileAdapter);
        fileModelSpinner.setSelection(indexOf(fileModels, fileKey));
        refreshStats();
    }

    /**
     * モデル配列から保存キーの位置を探します。
     * @param models 検索対象。例: {@code realtimeModels}
     * @param key 保存キー。例: {@code "external-a12b"}
     * @return 一致位置。不明時は0。例: {@code 4}
     */
    private static int indexOf(
            @NonNull final ITranscriptionModel[] models,
            final String key
    ) {
        for (int index = 0; index < models.length; index++) {
            if (models[index].key().equals(key)) {
                return index;
            }
        }
        return 0;
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
