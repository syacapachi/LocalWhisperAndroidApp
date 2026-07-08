package jp.ac.gifu_u.programmingjissen2.Record;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;
import events.AwaitEvent.AwaiterHub;
import events.Request.PermissionAwaiter;
import events.SystemEventHub;
import events.Whisper.WhisperRecordingStateEvent;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import jp.ac.gifu_u.programmingjissen2.Transcription.BackgroundWhisperService;
import jp.ac.gifu_u.programmingjissen2.R;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsActivity;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsStore;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperTranscriptionWorker;

/** 録音画面の UI とバックグラウンド Whisper サービスを接続するクラスです。 */
public class RecordActivity {
    /** AudioRecord と Whisper.cpp に渡す PCM のサンプリングレートです。 */
    static final int FREQUENCY = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;

    /** 録音権限要求で使う request code です。 */
    static final int REQUESTCODE = 2000;

    private final Activity activity;
    private final Button recordButton;
    private final Button settingsButton;
    private final RadioGroup modelRadioGroup;
    private final TextView resultTextView;
    private final TextView statusTextView;
    private final TextView benchmarkTextView;
    private final WhisperSettingsStore settingsStore;

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<WhisperRecordingStateEvent> stateListener = this::onRecordingStateEvent;

    private volatile RecordTranscriptionState state;
    private volatile boolean isTranscribing;
    private WhisperSettings currentSettings;
    private boolean updatingModelSelector;
    private WhisperFileTranscriptionWorker fileTranscriptionWorker;

    /** 従来の最小 UI で録音制御クラスを作成します。 */
    public RecordActivity(Activity activity, Button button, TextView resultTextView) {
        this(activity, new WhisperRecordControls(
                button,
                null,
                null,
                resultTextView,
                null,
                null
        ));
    }

    /** 録音 UI とバックグラウンド Whisper サービスの制御クラスを作成します。 */
    public RecordActivity(Activity activity, @NonNull WhisperRecordControls controls) {
        this.activity = activity;
        this.recordButton = controls.recordButton;
        this.settingsButton = controls.settingsButton;
        this.modelRadioGroup = controls.modelRadioGroup;
        this.resultTextView = controls.resultTextView;
        this.statusTextView = controls.statusTextView;
        this.benchmarkTextView = controls.benchmarkTextView;
        this.settingsStore = new WhisperSettingsStore(activity);
        this.currentSettings = settingsStore.load();

        SystemEventHub.subscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.subscribe(WhisperRecordingStateEvent.class, stateListener);

        setupRecordButton();
        setupSettingsButton();
        setupModelSelector();
        RefreshSettings();
    }

    private void setupRecordButton() {
        recordButton.setOnClickListener((view) -> {
            if (state != RecordTranscriptionState.Recording
                    && state != RecordTranscriptionState.Stopping) {
                if (StartRecord()) {
                    recordButton.setText("停止");
                }
            } else {
                if (StopRecord()) {
                    recordButton.setText("停止中");
                }
            }
        });
    }

    private void setupSettingsButton() {
        if (settingsButton == null) {
            return;
        }
        // 設定UI画面を上に重ねる
        settingsButton.setOnClickListener((view) -> activity.startActivity(
                new Intent(activity, WhisperSettingsActivity.class)
        ));
    }

    private void setupModelSelector() {
        syncModelSelector(currentSettings.model());
        if (modelRadioGroup == null) {
            return;
        }

        modelRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingModelSelector) {
                return;
            }

            final WhisperModelOption selected = modelFromRadioId(checkedId);
            if (selected == currentSettings.model()) {
                return;
            }

            if (isTranscribing) {
                outputMessage("モデル変更は録音停止後に反映できます");
                syncModelSelector(currentSettings.model());
                return;
            }

            currentSettings = currentSettings.withModel(selected);
            settingsStore.save(currentSettings);
            refreshWhisperInfo();
        });
    }

    /** 設定画面から戻ったときなどに、保存済み設定を録音画面へ反映します。 */
    public void RefreshSettings() {
        if (!isTranscribing) {
            currentSettings = settingsStore.load();
            syncModelSelector(currentSettings.model());
        }
        syncStateFromBackgroundService();
        recordButton.setText(state == RecordTranscriptionState.Recording
                ? "停止"
                : (state == RecordTranscriptionState.Stopping ? "停止中" : "録音"));
        refreshWhisperInfo();

        String latest = BackgroundWhisperService.latestText();
        if (latest != null && !latest.isEmpty()) {
            resultTextView.setText(latest);
        }
    }

    /** 録音権限を確認し、バックグラウンド録音サービスを開始します。 */
    public boolean StartRecord() {
        if (state == RecordTranscriptionState.Stopping) {
            outputMessage("停止処理中です");
            return false;
        }
        if (state == RecordTranscriptionState.FileTranscribing) {
            outputMessage("音声ファイル文字起こし中です");
            return false;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            final PermissionAwaiter awaiter = AwaiterHub.rentAwaiter(PermissionAwaiter.class);
            awaiter.initialize(
                    REQUESTCODE,
                    (result) -> {
                        if (result) {
                            requestPostNotificationPermissionIfNeeded();
                            activity.runOnUiThread(() -> {
                                if (StartRecord()) {
                                    recordButton.setText("停止");
                                }
                            });
                        } else {
                            outputMessage("録音権限が許可されていません");
                        }
                    }
            );
            awaiter.start();
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUESTCODE
            );
            return false;
        }

        requestPostNotificationPermissionIfNeeded();
        currentSettings = settingsStore.load();
        BackgroundWhisperService.startRecording(activity);
        setState(RecordTranscriptionState.Recording);
        recordButton.setText("停止");
        outputMessage(StringBufferBuilderPool.Join(
                "",
                "バックグラウンド録音を開始します: ",
                currentSettings.model().displayName()
        ));
        refreshWhisperInfo();
        return true;
    }

    /** バックグラウンド録音サービスへ停止を要求します。 */
    public boolean StopRecord() {
        if (state == RecordTranscriptionState.Stopping) {
            return true;
        }

        if (state != RecordTranscriptionState.Recording && !BackgroundWhisperService.isRunning()) {
            return false;
        }

        setState(RecordTranscriptionState.Stopping);
        BackgroundWhisperService.stopRecording(activity);
        recordButton.setText("停止中");
        refreshWhisperInfo();
        return true;
    }

    /**
     * 既存の音声ファイルを読み込み、Whisper 文字起こしを開始します。
     *
     * @param uri ドキュメントピッカーで選択された音声 URI。例: {@code content://media/external/audio/media/1}
     * @return 開始できた場合 true。例: {@code true}
     * @throws SecurityException URI の読み取り許可が失効している場合、worker 側でエラーイベントに変換します
     */
    public boolean TranscribeAudioFile(@NonNull final Uri uri) {
        if (state == RecordTranscriptionState.Recording
                || state == RecordTranscriptionState.Stopping
                || BackgroundWhisperService.isRunning()) {
            outputMessage("録音停止後に音声ファイルを文字起こしできます");
            return false;
        }
        if (state == RecordTranscriptionState.FileTranscribing) {
            outputMessage("音声ファイルを文字起こし中です");
            return false;
        }

        currentSettings = settingsStore.load();
        final String sessionId = StringBufferBuilderPool.Join(
                "-",
                "file",
                Long.toHexString(System.currentTimeMillis()),
                Long.toHexString(System.nanoTime())
        );
        fileTranscriptionWorker = new WhisperFileTranscriptionWorker(
                activity,
                uri,
                sessionId,
                currentSettings,
                this::onFileTranscriptionComplete
        );
        setState(RecordTranscriptionState.FileTranscribing);
        outputMessage("音声ファイルを読み込み中...");
        refreshWhisperInfo();

        if (!fileTranscriptionWorker.start()) {
            setState(null);
            fileTranscriptionWorker = null;
            outputMessage("音声ファイル文字起こしを開始できませんでした");
            refreshWhisperInfo();
            return false;
        }
        return true;
    }

    /** Activity の破棄時に UI 側の購読だけ解除します。 */
    public void Dispose() {
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.unsubscribe(WhisperRecordingStateEvent.class, stateListener);
    }

    private void onTranscriptionEvent(final WhisperTranscriptionEvent event) {
        activity.runOnUiThread(() -> {
            outputTranscription(event);
            if (event.tag() == WhisperTranscriptionTag.FileTranscribing && !event.hasError()) {
                settingsStore.recordInference(event.modelKey(), event.processingTimeMs());
            }
            refreshWhisperInfo();
        });
    }

    /**
     * ファイル文字起こし worker の完了通知を UI 状態へ反映します。
     *
     * @param sessionId 完了した session ID。例: {@code "file-1a2b"}
     * @param errorMessage エラー時のメッセージ。成功時は空文字。例: {@code "audio track not found"}
     */
    private void onFileTranscriptionComplete(final String sessionId, final String errorMessage) {
        activity.runOnUiThread(() -> {
            setState(null);
            fileTranscriptionWorker = null;
            if (errorMessage != null && !errorMessage.isEmpty()) {
                outputMessage(StringBufferBuilderPool.Join(
                        "",
                        "音声ファイル文字起こしに失敗しました: ",
                        errorMessage
                ));
            }
            refreshWhisperInfo();
        });
    }

    private void onRecordingStateEvent(WhisperRecordingStateEvent event) {
        activity.runOnUiThread(() -> {
            setState(event.stopping()
                    ? RecordTranscriptionState.Stopping
                    : (event.recording() ? RecordTranscriptionState.Recording : null));
            recordButton.setText(state == RecordTranscriptionState.Recording
                    ? "停止"
                    : (state == RecordTranscriptionState.Stopping ? "停止中" : "録音"));
            if (!event.latestText().isEmpty()) {
                resultTextView.setText(event.latestText());
            } else if (!event.message().isEmpty()) {
                resultTextView.setText(event.message());
            }
            refreshWhisperInfo();
        });
    }

    /** Whisper のイベント結果を画面に出力します。 */
    public void outputTranscription(@NonNull final WhisperTranscriptionEvent event) {
        if (event.hasError()) {
            resultTextView.setText(StringBufferBuilderPool.Join(
                    "",
                    "Whisper エラー: ",
                    event.errorMessage()
            ));
            return;
        }

        final String label = event.finalResult() ? "最終結果" : "認識中";
        final String text = event.text().isEmpty() ? "..." : event.text();
        resultTextView.setText(buildTranscriptionViewText(label, text, event));
    }

    @NonNull
    private String buildTranscriptionViewText(
            @NonNull final String label,
            @NonNull final String text,
            @NonNull final WhisperTranscriptionEvent event
    ) {
        WhisperModelOption model = WhisperModelOption.fromKey(event.modelKey());
        return StringBufferBuilderPool.Join(
                "",
                label,
                " ",
                event.startMs(),
                "ms-",
                event.startMs() + event.durationMs(),
                "ms\n",
                "モデル: ",
                model.displayName(),
                " / 推論: ",
                event.processingTimeMs(),
                "ms\n",
                text
        );
    }

    private void refreshWhisperInfo() {
        final WhisperSettings settings = currentSettings;
        final String stateText = stateText();
        setStatusText(StringBufferBuilderPool.Join(
                "",
                "状態: ",
                stateText,
                " / モデル: ",
                settings.model().displayName(),
                " / 言語: ",
                settings.language(),
                " / 窓: ",
                settings.windowMs(),
                "ms / 重なり: ",
                settings.overlapMs(),
                "ms"
        ));
        setBenchmarkText(buildBenchmarkText());
    }

    @NonNull
    private String buildBenchmarkText() {
        return StringBufferBuilderPool.Join(
                "",
                formatStats(WhisperModelOption.BASE),
                "\n",
                formatStats(WhisperModelOption.SMALL)
        );
    }

    @NonNull
    private String formatStats(final WhisperModelOption model) {
        final WhisperInferenceStats stats = settingsStore.loadStats(model);
        if (!stats.hasSamples()) {
            return StringBufferBuilderPool.Join(
                    "",
                    model.displayName(),
                    ": 推論時間 未計測"
            );
        }

        return StringBufferBuilderPool.Join(
                "",
                model.displayName(),
                ": 最新 ",
                stats.lastMs(),
                "ms / 平均 ",
                stats.averageMs(),
                "ms / ",
                stats.count(),
                "回"
        );
    }

    private void syncModelSelector(final WhisperModelOption model) {
        if (modelRadioGroup == null) {
            return;
        }

        updatingModelSelector = true;
        modelRadioGroup.check(model == WhisperModelOption.SMALL
                ? R.id.whisperModelSmall
                : R.id.whisperModelBase);
        updatingModelSelector = false;
    }

    private WhisperModelOption modelFromRadioId(int checkedId) {
        if (checkedId == R.id.whisperModelSmall) {
            return WhisperModelOption.SMALL;
        }
        return WhisperModelOption.BASE;
    }

    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUESTCODE + 1
        );
    }

    private void outputMessage(final String message) {
        activity.runOnUiThread(() -> resultTextView.setText(message));
    }

    /**
     * 録音サービスの状態を RecordActivity のステートへ反映します。
     */
    private void syncStateFromBackgroundService() {
        if (state == RecordTranscriptionState.FileTranscribing) {
            return;
        }
        setState(BackgroundWhisperService.isStopping()
                ? RecordTranscriptionState.Stopping
                : (BackgroundWhisperService.isRunning() ? RecordTranscriptionState.Recording : null));
    }

    /**
     * 文字起こし状態を更新し、isTranscribing も同期します。
     *
     * @param nextState 次の状態。例: {@code RecordTranscriptionState.Recording}
     */
    private void setState(final RecordTranscriptionState nextState) {
        state = nextState;
        isTranscribing = nextState != null;
    }

    /**
     * 現在のステートを画面表示用テキストへ変換します。
     *
     * @return 表示用ステート。例: {@code "録音中"}
     */
    @NonNull
    private String stateText() {
        if (state == RecordTranscriptionState.Recording) {
            return "録音中";
        }
        if (state == RecordTranscriptionState.FileTranscribing) {
            return "ファイル文字起こし中";
        }
        if (state == RecordTranscriptionState.Stopping) {
            return "停止中";
        }
        return "待機中";
    }

    private void setStatusText(final String value) {
        if (statusTextView != null) {
            statusTextView.setText(value);
        }
    }

    private void setBenchmarkText(final String value) {
        if (benchmarkTextView != null) {
            benchmarkTextView.setText(value);
        }
    }
}
