package jp.ac.gifu_u.programmingjissen2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.function.Consumer;

import events.AwaitEvent.AwaiterHub;
import events.Request.RequestPermissionResultEvent;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperProgressEvent;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.FileImport.ExternalMediaIntentReader;
import jp.ac.gifu_u.programmingjissen2.MainUI.MainScreenView;
import jp.ac.gifu_u.programmingjissen2.MainUI.QuickStartTabView;
import jp.ac.gifu_u.programmingjissen2.Record.RecordActivity;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsStore;
import jp.ac.gifu_u.programmingjissen2.Transcription.BackgroundWhisperService;

/** 録音実行、進捗、履歴と、任意のJSONデバッグ結果をタブ表示するメインActivityです。 */
public final class MainActivity extends AppCompatActivity {
    /** アプリの画面UIクラス */
    private MainScreenView screen;
    /** 録音クラス */
    private RecordActivity recordActivity;
    /** 設定 */
    private WhisperSettingsStore settingsStore;
    /** ファイルを開くPicker URIが入る */
    private ActivityResultLauncher<String[]> audioFilePicker;
    /** 画面キャプチャを行うLauncher,キャプチャするアプリのIntentを受け取る */
    private ActivityResultLauncher<Intent> mediaProjectionPermissionLauncher;
    private boolean bindingQuickSettings;

    private final Consumer<WhisperProgressEvent> progressListener = this::onProgress;
    private final Consumer<WhisperTranscriptionEvent> resultListener = this::onResult;
    private final Consumer<ThreadStoppedEvent> stoppedListener = this::onThreadStopped;

    /** @param savedInstanceState Android復元状態。初回例: {@code null} */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //画面上部のアクションバーを隠す
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // 動画・音声ファイルを選択する方法と、選択した時の処理を登録します。
        audioFilePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onAudioFileSelected);
        // 画面キャプチャと、選択した時の処理を登録します。
        mediaProjectionPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onMediaProjectionPermissionResult);
        // アプリ内の設定をロード
        settingsStore = new WhisperSettingsStore(this);
        // UI設定
        screen = new MainScreenView(this);
        screen.setDebugHistoryVisible(settingsStore.isDebugResultVisible());
        super.setContentView(screen.view());
        final QuickStartTabView quick = screen.quickStart();
        // ボタンにイベントリスナーを登録(動画・音声ファイルの選択)
        quick.fileButton().setOnClickListener(view -> audioFilePicker.launch(
                new String[]{"audio/*", "video/*", "application/octet-stream"}));

        recordActivity = new RecordActivity(this, quick.controls(screen.settingsButton()));
        recordActivity.setProjectionPermissionLauncher(mediaProjectionPermissionLauncher);
        bindQuickSettings(settingsStore.load());
        setupQuickSettingsSaving();
        // 推論の進捗を反映
        SystemEventHub.subscribe(WhisperProgressEvent.class, progressListener);
        // 推論結果を反映
        SystemEventHub.subscribe(WhisperTranscriptionEvent.class, resultListener);
        // ファイル書き込みが終了したら再読み込み
        SystemEventHub.subscribe(ThreadStoppedEvent.class, stoppedListener);
        // 「アプリで開く」の場合、super.getIntent()がnullでない。
        handleExternalMediaIntent(super.getIntent());
    }

    /** 設定画面から戻った場合もクイック設定と履歴を最新状態へ更新します。 */
    @Override
    protected void onResume() {
        super.onResume();
        if (recordActivity != null) {
            recordActivity.RefreshSettings();
        }
        if (settingsStore != null && screen != null) {
            bindQuickSettings(settingsStore.load());
            screen.setDebugHistoryVisible(settingsStore.isDebugResultVisible());
            screen.refreshHistories();
        }
    }

    /** イベント購読と録音画面controllerを解放します。 */
    @Override
    protected void onDestroy() {
        SystemEventHub.unsubscribe(WhisperProgressEvent.class, progressListener);
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, resultListener);
        SystemEventHub.unsubscribe(ThreadStoppedEvent.class, stoppedListener);
        if (recordActivity != null) {
            recordActivity.Dispose();
        }
        if (!BackgroundWhisperService.isServiceActive()) {
            AwaiterHub.clear();
            SystemEventHub.clear();
        }
        super.onDestroy();
    }

    /**
     * 起動済みActivityへ届いた外部音声・動画Intentを処理します。
     * @param intent ACTION_VIEW Intent。例: {@code new Intent(Intent.ACTION_VIEW, uri)}
     */
    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        super.setIntent(intent);
        handleExternalMediaIntent(intent);
    }

    /** クイック設定の入力変更をSharedPreferencesへ即時保存するlistenerを設定します。 */
    private void setupQuickSettingsSaving() {
        final TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                saveQuickSettings();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        screen.quickStart().windowEdit().addTextChangedListener(watcher);
        screen.quickStart().vadThresholdEdit().addTextChangedListener(watcher);
        screen.quickStart().recordAndRetranscribeSwitch().setOnCheckedChangeListener(
                (button, checked) -> saveQuickSettings());
    }

    /**
     * 保存済み値をクイックスタートへ表示します。
     * @param settings 表示値。例: {@code WhisperSettings.defaultSettings()}
     */
    private void bindQuickSettings(@NonNull final WhisperSettings settings) {
        bindingQuickSettings = true;
        screen.quickStart().bindSettings(
                settings.windowMs(), settings.vadThreshold(),
                settings.audioRecordingEnabled() && settings.autoRetranscribeEnabled());
        bindingQuickSettings = false;
    }

    /** 入力中のクイック設定を検証し、リアルタイム設定へ保存します。 */
    private void saveQuickSettings() {
        if (bindingQuickSettings || settingsStore == null || screen == null) {
            return;
        }
        final WhisperSettings current = settingsStore.load();
        final int windowMs = (int) Math.round(
                screen.quickStart().windowControl().valueOr(current.windowMs() / 1000.0)
                        * 1000.0);
        final float vad = (float) screen.quickStart().vadThresholdControl().valueOr(
                current.vadThreshold());
        final boolean retranscribe =
                screen.quickStart().recordAndRetranscribeSwitch().isChecked();
        settingsStore.save(current.withQuickSettings(windowMs, vad, retranscribe, retranscribe));
    }

    /** @param event 推論開始進捗。例: {@code WhisperProgressEvent} */
    private void onProgress(@NonNull final WhisperProgressEvent event) {
        runOnUiThread(() -> screen.quickStart().showProgress(event));
    }

    /** @param event 推論完了結果。例: {@code WhisperTranscriptionEvent} */
    private void onResult(@NonNull final WhisperTranscriptionEvent event) {
        runOnUiThread(() -> {
            screen.quickStart().showCompleted(event);
            if (event.finalResult()) {
                screen.refreshHistories();
            }
        });
    }

    /** @param event Worker停止通知。JSON保存完了例: {@code owner="Json"} */
    private void onThreadStopped(@NonNull final ThreadStoppedEvent event) {
        if ("Json".equals(event.owner())) {
            runOnUiThread(screen::refreshHistories);
        }
    }

    /** @param uri 選択音声へのアクセス権限取得します。例: {@code content://media/1} */
    private void onAudioFileSelected(final Uri uri) {
        if (uri == null || recordActivity == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        recordActivity.TranscribeAudioFile(uri);
    }

    /**
     * 外部の「アプリで開く」Intentをファイル推論へ渡します。
     * @param intent 解析対象。例: {@code new Intent(Intent.ACTION_VIEW, uri)}
     * @return 対応URIを受理できた場合true。例: {@code true}
     */
    private boolean handleExternalMediaIntent(final Intent intent) {
        if (recordActivity == null) {
            return false;
        }
        // IntentをUriに変換
        final Uri uri = ExternalMediaIntentReader.readSupportedUri(intent);
        return uri != null && recordActivity.TranscribeAudioFile(uri);
    }

    /** @param result MediaProjection結果。例: {@code ActivityResult} */
    private void onMediaProjectionPermissionResult(@NonNull final ActivityResult result) {
        if (recordActivity != null) {
            recordActivity.onMediaProjectionPermissionResult(
                    result.getResultCode(), result.getData());
        }
    }

    /** @param requestCode 例: {@code 2000} @param permissions 権限名配列 @param grantResults 結果配列 */
    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String[] permissions,
            @NonNull final int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SystemEventHub.publish(new RequestPermissionResultEvent(
                requestCode, permissions, grantResults));
    }
}
