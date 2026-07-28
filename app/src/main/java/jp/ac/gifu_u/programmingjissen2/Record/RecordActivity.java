package jp.ac.gifu_u.programmingjissen2.Record;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

import Utils.ScopableUtility;
import Utils.StringPool.StringBufferBuilderPool;
import events.AwaitEvent.AwaiterHub;
import events.Request.PermissionAwaiter;
import events.SystemEventHub;
import events.Whisper.WhisperRecordingStateEvent;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import jp.ac.gifu_u.programmingjissen2.Transcription.BackgroundWhisperService;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.SettingUI.ExternalModelRepository;
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
    private final RecordScreenBinder screenBinder;
    private final WhisperSettingsStore settingsStore;

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<WhisperRecordingStateEvent> stateListener = this::onRecordingStateEvent;

    private volatile RecordTranscriptionState state;
    private volatile boolean isTranscribing;
    private volatile boolean recording;
    private volatile boolean inferenceAlive;
    private volatile boolean inferenceAccepting;
    private WhisperSettings currentSettings;
    private WhisperFileTranscriptionWorker fileTranscriptionWorker;
    private ActivityResultLauncher<Intent> projectionPermissionLauncher;
    private RecordingAudioSource pendingAudioSource;

    /** 従来の最小 UI で録音制御クラスを作成します。 */
    public RecordActivity(Activity activity, Button button, TextView resultTextView) {
        this(activity, new WhisperRecordControls(
                button,
                null,
                null,
                null,
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
        this.screenBinder = new RecordScreenBinder(controls);
        this.settingsStore = new WhisperSettingsStore(activity);
        this.currentSettings = settingsStore.load();

        SystemEventHub.subscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.subscribe(WhisperRecordingStateEvent.class, stateListener);

        setupRecordButton();
        setupInferenceButton();
        setupSettingsButton();
        setupModelSelector();
        setupAudioSourceSelectors();
        RefreshSettings();
    }

    /**
     * MediaProjection許可画面を起動するlauncherを設定します。
     * @param launcher Activityに登録済みlauncher。例: {@code registerForActivityResult(new StartActivityForResult(), callback)}
     */
    public void setProjectionPermissionLauncher(
            @NonNull final ActivityResultLauncher<Intent> launcher
    ) {
        projectionPermissionLauncher = launcher;
    }

    private void setupRecordButton() {
        screenBinder.setRecordClickListener((view) -> {
            if (recording) {
                StopRecord();
            } else {
                StartRecord();
            }
        });
    }

    private void setupInferenceButton() {
        screenBinder.setInferenceClickListener((view) -> {
            if (inferenceAccepting) {
                StopInference();
            } else {
                ResumeInference();
            }
        });
    }

    private void setupSettingsButton() {
        // 設定UI画面を上に重ねる
        screenBinder.setSettingsClickListener((view) -> activity.startActivity(
                new Intent(activity, WhisperSettingsActivity.class)
        ));
    }

    private void setupModelSelector() {
        screenBinder.bindModelSelector(currentSettings.model(), (selected) -> {
            if (selected.key().equals(currentSettings.model().key())) {
                return;
            }

            if (isTranscribing) {
                outputMessage("モデル変更は録音停止後に反映できます");
                screenBinder.syncModelSelector(currentSettings.model());
                return;
            }

            currentSettings = currentSettings.withModel(selected);
            settingsStore.save(currentSettings);
            refreshWhisperInfo();
        });
    }

    /** 録音入力プルダウンだけを設定し、キャプチャ対象選択はMediaProjectionのOS画面へ任せます。 */
    private void setupAudioSourceSelectors() {
        screenBinder.bindAudioSourceSelectors(
                java.util.Collections.emptyList(),
                source -> { }
        );
    }

    /** 設定画面から戻ったときなどに、保存済み設定を録音画面へ反映します。 */
    public void RefreshSettings() {
        if (!isTranscribing) {
            currentSettings = settingsStore.load();
            setupModelSelector();
        }
        syncStateFromBackgroundService();
        screenBinder.setRecordButtonState(state);
        screenBinder.setInferenceButtonState(recording, inferenceAlive, inferenceAccepting);
        screenBinder.setAudioSourceSelectorsEnabled(!recording);
        refreshWhisperInfo();

        String latest = BackgroundWhisperService.latestText();
        if (latest != null && !latest.isEmpty()) {
            screenBinder.showMessage(latest);
        }
    }

    /** 録音権限を確認し、バックグラウンド録音サービスを開始します。 */
    public boolean StartRecord() {
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
                                    screenBinder.setRecordButtonState(state);
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
        final RecordingAudioSource source = screenBinder.selectedAudioSource();
        if (source.requiresAppCapture()) {
            if (projectionPermissionLauncher == null) {
                outputMessage("キャプチャ許可画面を開始できません");
                return false;
            }
            pendingAudioSource = source;
            //　キャプチャアプリのマネージャークラス
            final MediaProjectionManager manager =
                    (MediaProjectionManager) activity.getSystemService(
                            Activity.MEDIA_PROJECTION_SERVICE
                    );
            // キャプチャの許可画面を表示
            projectionPermissionLauncher.launch(manager.createScreenCaptureIntent());
            outputMessage("対象アプリ音声のキャプチャを許可してください");
            return false;
        }
        return startBackgroundRecording(source, null, Activity.RESULT_CANCELED, null);
    }

    /**
     * MediaProjection許可結果を使ってアプリ音声録音を開始します。
     * @param resultCode Activity結果。例: {@code Activity.RESULT_OK}
     * @param data MediaProjection tokenを含むIntent。例: {@code result.getData()}
     */
    public void onMediaProjectionPermissionResult(final int resultCode, final Intent data) {
        final RecordingAudioSource source = pendingAudioSource;
        pendingAudioSource = null;
        if (resultCode != Activity.RESULT_OK || data == null || source == null) {
            outputMessage("アプリ音声のキャプチャが許可されませんでした");
            return;
        }
        startBackgroundRecording(source, null, resultCode, data);
    }

    /**
     * 選択済み入力をForeground Serviceへ渡してUIを録音中にします。
     * @param source 音声入力。例: {@code RecordingAudioSource.MICROPHONE_AND_APP}
     * @param target 対象アプリ。マイクのみならnull。例: {@code new CaptureTargetApp("YouTube", "com.google.android.youtube", 10123)}
     * @param resultCode MediaProjection結果。例: {@code Activity.RESULT_OK}
     * @param projectionData MediaProjection token。マイクのみならnull。例: {@code resultIntent}
     * @return 開始要求を送れた場合true。例: {@code true}
     */
    private boolean startBackgroundRecording(
            @NonNull final RecordingAudioSource source,
            final CaptureTargetApp target,
            final int resultCode,
            final Intent projectionData
    ) {
        BackgroundWhisperService.startRecording(
                activity,
                source,
                target == null ? -1 : target.uid(),
                resultCode,
                projectionData
        );
        recording = true;
        inferenceAlive = true;
        inferenceAccepting = true;
        setState(RecordTranscriptionState.Recording);
        screenBinder.setRecordButtonState(state);
        screenBinder.setInferenceButtonState(recording, inferenceAlive, inferenceAccepting);
        screenBinder.setAudioSourceSelectorsEnabled(false);
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
        if (!recording && !BackgroundWhisperService.isRunning()) {
            return false;
        }

        recording = false;
        setState(RecordTranscriptionState.StopRecord);
        BackgroundWhisperService.stopRecording(activity);
        screenBinder.setRecordButtonState(state);
        screenBinder.setInferenceButtonState(recording, inferenceAlive, false);
        screenBinder.setAudioSourceSelectorsEnabled(true);
        refreshWhisperInfo();
        return true;
    }

    /**
     * リアルタイム推論への新規音声投入を止め、キュー排出後の停止を要求します。
     *
     * @return 録音中に停止要求を送れた場合true。例: {@code true}
     */
    public boolean StopInference() {
        if (!recording || !inferenceAlive) {
            return false;
        }
        inferenceAccepting = false;
        BackgroundWhisperService.stopInference(activity);
        screenBinder.setInferenceButtonState(recording, inferenceAlive, false);
        refreshWhisperInfo();
        return true;
    }

    /**
     * 録音中のリアルタイム推論を再開します。
     *
     * @return 再開要求を送れた場合true。録音していない場合false。例: {@code true}
     */
    public boolean ResumeInference() {
        if (!recording) {
            outputMessage("録音中のみ推論を再開できます");
            return false;
        }
        inferenceAlive = true;
        inferenceAccepting = true;
        BackgroundWhisperService.resumeInference(activity);
        screenBinder.setInferenceButtonState(recording, true, true);
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
        if (recording || inferenceAlive || BackgroundWhisperService.isServiceActive()) {
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
            screenBinder.showTranscription(event);
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
            recording = event.recording();
            inferenceAlive = event.inferenceAlive();
            inferenceAccepting = event.inferenceAccepting();
            setState(event.stopping()
                    ? RecordTranscriptionState.StopRecord
                    : (event.recording() ? RecordTranscriptionState.Recording : null));
            screenBinder.setRecordButtonState(state);
            screenBinder.setInferenceButtonState(
                    recording,
                    inferenceAlive,
                    inferenceAccepting
            );
            screenBinder.setAudioSourceSelectorsEnabled(!recording);
            if (!event.latestText().isEmpty()) {
                screenBinder.showMessage(event.latestText());
            } else if (!event.message().isEmpty()) {
                screenBinder.showMessage(event.message());
            }
            refreshWhisperInfo();
        });
    }

    private void refreshWhisperInfo() {
        final WhisperSettings settings = currentSettings;
        final String stateText = stateText();
        if (state == RecordTranscriptionState.FileTranscribing) {
            setStatusText(StringBufferBuilderPool.Join(
                    "",
                    "状態: ",
                    stateText,
                    " / モデル: ",
                    settings.fileTranscription().model(),
                    " / 言語: ",
                    settings.fileTranscription().language(),
                    " / 一括推論"
            ));
            screenBinder.setBenchmarkText(buildBenchmarkText());
            return;
        }
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
        screenBinder.setBenchmarkText(buildBenchmarkText());
    }

    @NonNull
    private String buildBenchmarkText() {
        try(StringBufferBuilderPool builder = ScopableUtility.getBuilder()){
            final ITranscriptionModel[] models = new ExternalModelRepository(
                    activity).list(WhisperInferenceEngine.CTRANSLATE2);
            for (ITranscriptionModel model : models) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(formatStats(model));
            }
            return builder.toString();
        }
    }

    @NonNull
    private String formatStats(final ITranscriptionModel model) {
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
        activity.runOnUiThread(() -> screenBinder.showMessage(message));
    }

    /**
     * 録音サービスの状態を RecordActivity のステートへ反映します。
     */
    private void syncStateFromBackgroundService() {
        if (state == RecordTranscriptionState.FileTranscribing) {
            return;
        }
        recording = BackgroundWhisperService.isRunning();
        inferenceAlive = BackgroundWhisperService.isInferenceAlive();
        inferenceAccepting = BackgroundWhisperService.isInferenceAccepting();
        setState(BackgroundWhisperService.isStopping()
                ? BackgroundWhisperService.currentState()
                : (recording ? RecordTranscriptionState.Recording : null));
    }

    /**
     * 文字起こし状態を更新し、isTranscribing も同期します。
     *
     * @param nextState 次の状態。例: {@code RecordTranscriptionState.Recording}
     */
    private void setState(final RecordTranscriptionState nextState) {
        state = nextState;
        isTranscribing = nextState == RecordTranscriptionState.FileTranscribing
                || recording
                || inferenceAlive;
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
        if (state == RecordTranscriptionState.StopRecord) {
            return "録音停止";
        }
        if (state == RecordTranscriptionState.StopAll) {
            return recording ? "録音中（推論停止）" : "推論停止";
        }
        return "待機中";
    }

    private void setStatusText(final String value) {
        screenBinder.setStatusText(value);
    }
}
