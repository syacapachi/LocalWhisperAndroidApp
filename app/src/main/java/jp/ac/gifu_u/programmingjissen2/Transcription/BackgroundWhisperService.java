package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.function.Consumer;

import Utils.MyUtils;
import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperRecordingStateEvent;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.Record.AudioRecordWorker;
import jp.ac.gifu_u.programmingjissen2.Record.RecordTranscriptionState;
import jp.ac.gifu_u.programmingjissen2.Record.RecordedAudioFileWriter;
import jp.ac.gifu_u.programmingjissen2.Record.RecordingAudioSource;
import jp.ac.gifu_u.programmingjissen2.Record.WhisperFileTranscriptionWorker;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsStore;
import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.TranscriptionJsonWorker;

/** バックグラウンド録音、リアルタイム推論、録音後再推論を管理するForeground Serviceです。 */
public class BackgroundWhisperService extends Service {
    private static final String TAG = BackgroundWhisperService.class.getSimpleName();

    public static final String ACTION_START = "jp.ac.gifu_u.programmingjissen2.whisper.START";
    public static final String ACTION_STOP = "jp.ac.gifu_u.programmingjissen2.whisper.STOP";
    public static final String ACTION_STOP_INFERENCE =
            "jp.ac.gifu_u.programmingjissen2.whisper.STOP_INFERENCE";
    public static final String ACTION_RESUME_INFERENCE =
            "jp.ac.gifu_u.programmingjissen2.whisper.RESUME_INFERENCE";
    private static final String EXTRA_AUDIO_SOURCE = "audioSource";
    private static final String EXTRA_CAPTURE_TARGET_UID = "captureTargetUid";
    private static final String EXTRA_PROJECTION_RESULT_CODE = "projectionResultCode";
    private static final String EXTRA_PROJECTION_DATA = "projectionData";

    private static volatile boolean active;
    private static volatile boolean recording;
    private static volatile boolean inferenceAlive;
    private static volatile boolean inferenceAccepting;
    private static volatile RecordTranscriptionState currentState;
    private static volatile String latestText = "";
    private static volatile String currentModelKey = "";

    private WhisperSettingsStore settingsStore;
    private WhisperForegroundNotification notificationController;
    private WhisperSettings settings;
    private String modelPath;
    private String vadModelPath;
    private String recordingSessionId;
    private String inferenceSessionId;
    private String retranscriptionSessionId;
    private int inferenceSequence;

    private AudioRecordWorker recordWorker;
    private WhisperTranscriptionWorker transcriptionWorker;
    private TranscriptionJsonWorker transcriptionJsonWorker;
    private volatile RecordedAudioFileWriter audioFileWriter;
    private WhisperFileTranscriptionWorker retranscriptionWorker;
    private RecordingAudioSource requestedAudioSource = RecordingAudioSource.MICROPHONE;
    private int requestedCaptureTargetUid = -1;
    private int projectionResultCode = Activity.RESULT_CANCELED;
    private Intent projectionResultData;
    private MediaProjection mediaProjection;
    private MediaProjection.Callback mediaProjectionCallback;

    private boolean pendingRecordingStart;
    private boolean pendingInferenceResume;
    private boolean inferenceFailed;
    private boolean whisperThreadStopped = true;
    private boolean jsonThreadStopped = true;
    private final ArrayDeque<RetranscriptionRequest> retranscriptionQueue = new ArrayDeque<>();

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<ThreadStoppedEvent> threadStoppedListener = this::onThreadStopped;

    /** @param context 開始要求元。例: {@code activity} */
    public static void startRecording(final Context context) {
        startRecording(context, RecordingAudioSource.MICROPHONE,
                -1, Activity.RESULT_CANCELED, null);
    }

    /**
     * 録音入力とMediaProjection許可を指定してForeground Serviceを開始します。
     * @param context 開始要求元。例: {@code activity}
     * @param source 音声入力。例: {@code RecordingAudioSource.APP_CAPTURE}
     * @param captureTargetUid 対象UID。マイクのみなら-1。例: {@code 10123}
     * @param resultCode MediaProjection結果。例: {@code Activity.RESULT_OK}
     * @param projectionData MediaProjection token。マイクのみならnull。例: {@code resultIntent}
     */
    public static void startRecording(
            @NonNull final Context context,
            @NonNull final RecordingAudioSource source,
            final int captureTargetUid,
            final int resultCode,
            @Nullable final Intent projectionData
    ) {
        final Intent intent = new Intent(context, BackgroundWhisperService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_AUDIO_SOURCE, source.name())
                .putExtra(EXTRA_CAPTURE_TARGET_UID, captureTargetUid)
                .putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode);
        if (projectionData != null) {
            intent.putExtra(EXTRA_PROJECTION_DATA, projectionData);
        }
        ContextCompat.startForegroundService(context, intent);
    }

    /** @param context 停止要求元。例: {@code activity} */
    public static void stopRecording(final Context context) {
        sendAction(context, ACTION_STOP, false);
    }

    /** @param context 推論停止要求元。例: {@code activity} */
    public static void stopInference(final Context context) {
        sendAction(context, ACTION_STOP_INFERENCE, false);
    }

    /** @param context 推論再開要求元。例: {@code activity} */
    public static void resumeInference(final Context context) {
        sendAction(context, ACTION_RESUME_INFERENCE, false);
    }

    public static boolean isRunning() { return active && recording; }
    public static boolean isInferenceAlive() { return active && inferenceAlive; }
    public static boolean isInferenceAccepting() { return active && inferenceAccepting; }
    public static boolean isStopping() { return active && !recording && inferenceAlive; }
    public static RecordTranscriptionState currentState() { return currentState; }
    public static boolean isServiceActive() { return active; }
    public static String latestText() { return latestText; }
    public static String currentModelKey() { return currentModelKey; }

    @Override
    public void onCreate() {
        super.onCreate();
        settingsStore = new WhisperSettingsStore(this);
        notificationController = new WhisperForegroundNotification(this);
        notificationController.createChannel();
        active = true;
        SystemEventHub.subscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.subscribe(ThreadStoppedEvent.class, threadStoppedListener);
    }

    /**
     *
     * @param intent The Intent supplied to {@link android.content.Context#startService},
     * as given.  This may be null if the service is being restarted after
     * its process has gone away, and it had previously returned anything
     * except {@link #START_STICKY_COMPATIBILITY}.
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to
     * start.  Use with {@link #stopSelfResult(int)}.
     *
     * @return 開始したかどうか
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        final String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            requestStopRecording();
        } else if (ACTION_STOP_INFERENCE.equals(action)) {
            requestStopInference();
        } else if (ACTION_RESUME_INFERENCE.equals(action)) {
            requestResumeInference();
        } else {
            readRecordingRequest(intent);
            notificationController.start(
                    "Whisper 録音を準備中",
                    "モデルを準備しています",
                    recording,
                    foregroundServiceTypes(requestedAudioSource)
            );
            startRecordingInternalAsync();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.unsubscribe(ThreadStoppedEvent.class, threadStoppedListener);
        closeAudioFile(false);
        releaseMediaProjection();
        active = false;
        recording = false;
        inferenceAlive = false;
        inferenceAccepting = false;
        currentState = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) { return null; }

    /**
     * Serviceへ操作Intentを送ります。
     * @param context 要求元。例: {@code activity}
     * @param action 操作。例: {@code ACTION_START}
     * @param foreground Foreground Service開始が必要ならtrue。例: {@code true}
     */
    private static void sendAction(
            @NonNull final Context context,
            @NonNull final String action,
            final boolean foreground
    ) {
        final Intent intent = new Intent(context, BackgroundWhisperService.class);
        intent.setAction(action);
        if (foreground) {
            ContextCompat.startForegroundService(context, intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 録音開始Intentから入力・対象UID・MediaProjection結果を読み込みます。
     * @param intent 開始Intent。例: {@code new Intent().putExtra("audioSource", "APP_CAPTURE")}
     */
    @SuppressWarnings("deprecation")
    private void readRecordingRequest(@Nullable final Intent intent) {
        if (intent == null) {
            requestedAudioSource = RecordingAudioSource.MICROPHONE;
            requestedCaptureTargetUid = -1;
            projectionResultCode = Activity.RESULT_CANCELED;
            projectionResultData = null;
            return;
        }
        try {
            requestedAudioSource = RecordingAudioSource.fromName(
                    intent.getStringExtra(EXTRA_AUDIO_SOURCE));
        } catch (IllegalArgumentException e) {
            requestedAudioSource = RecordingAudioSource.MICROPHONE;
        }
        requestedCaptureTargetUid = intent.getIntExtra(EXTRA_CAPTURE_TARGET_UID, -1);
        projectionResultCode = intent.getIntExtra(
                EXTRA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED);
        projectionResultData = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
    }

    /**
     * 入力に必要なForeground Service typeを返します。
     * @param source 音声入力。例: {@code RecordingAudioSource.MICROPHONE_AND_APP}
     * @return ServiceInfoのbit mask。例: {@code FOREGROUND_SERVICE_TYPE_MICROPHONE | FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION}
     */
    private static int foregroundServiceTypes(@NonNull final RecordingAudioSource source) {
        if (source == RecordingAudioSource.APP_CAPTURE) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        if (source == RecordingAudioSource.MICROPHONE_AND_APP) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
    }

    /** 録音開始処理を専用スレッドへ渡します。 */
    private void startRecordingInternalAsync() {
        new Thread(this::startRecordingInternal, "BackgroundWhisperStart").start();
    }

    /** マイク、推論worker、必要ならWAV保存先を準備して録音を開始します。 */
    private synchronized void startRecordingInternal() {
        if (recording) {
            publishState("録音中です");
            return;
        }
        if (recordWorker != null && recordWorker.isAlive()) {
            pendingRecordingStart = true;
            publishState("前の録音停止後に録音を再開します");
            return;
        }
        if (retranscriptionWorker != null && retranscriptionWorker.isAlive()) {
            pendingRecordingStart = true;
            publishState("保存音声の再推論後に録音を開始します");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            publishState("録音権限がありません");
            stopForegroundAndSelf();
            return;
        }
        settings = settingsStore.load();
        currentModelKey = settings.model().key();
        try {
            prepareModels();
        } catch (IOException e) {
            Log.e(TAG, "Whisper model prepare failed", e);
            publishState("モデル準備に失敗しました: " + e.getMessage());
            stopForegroundAndSelf();
            return;
        }

        if (!ensureInferenceAccepting()) {
            pendingRecordingStart = true;
            publishState("推論workerの再起動後に録音を開始します");
            return;
        }

        final int sampleRate = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;
        final int bufferSize = AudioRecordWorker.createBufferSize(sampleRate);
        if (bufferSize <= 0) {
            publishState("録音バッファを作成できませんでした");
            stopInferenceIfNotRecording();
            return;
        }

        recordingSessionId = newSessionId("record");
        if (!openAudioFile(recordingSessionId, sampleRate)) {
            stopInferenceIfNotRecording();
            return;
        }
        if (!prepareMediaProjectionIfNeeded()) {
            closeAudioFile(false);
            publishState("アプリ音声のキャプチャ許可を開始できませんでした");
            stopInferenceIfNotRecording();
            return;
        }
        pendingRecordingStart = false;
        recordWorker = new AudioRecordWorker(
                sampleRate,
                bufferSize,
                recordingSessionId + ":record",
                this::onAudioChunk,
                requestedAudioSource,
                mediaProjection,
                requestedCaptureTargetUid
        );
        if (!recordWorker.start()) {
            recordWorker = null;
            releaseMediaProjection();
            closeAudioFile(false);
            publishState("AudioRecord の初期化に失敗しました");
            stopInferenceIfNotRecording();
            return;
        }

        recording = true;
        currentState = RecordTranscriptionState.Recording;
        notificationController.update("録音中...", true);
        publishState("バックグラウンド録音中");
    }

    /** マイク停止後に推論キューを排出停止させます。 */
    private synchronized void requestStopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        pendingRecordingStart = false;
        currentState = RecordTranscriptionState.StopRecord;
        publishState("録音停止: 残り音声を推論中です");
        requestRecordThreadStop();
    }

    /** 録音を続けたまま推論への投入を止め、推論workerを排出停止させます。 */
    private synchronized void requestStopInference() {
        if (!recording || transcriptionWorker == null) {
            return;
        }
        pendingInferenceResume = false;
        inferenceAccepting = false;
        currentState = RecordTranscriptionState.StopAll;
        transcriptionWorker.requestStop();
        publishState("推論停止: キュー排出後にworkerを停止します");
    }

    /** 録音中の推論投入を再開し、必要なら推論workerを再作成します。 */
    private synchronized void requestResumeInference() {
        if (!recording) {
            publishState("録音中のみ推論を再開できます");
            return;
        }
        pendingInferenceResume = true;
        if (ensureInferenceAccepting()) {
            pendingInferenceResume = false;
            currentState = RecordTranscriptionState.Recording;
            publishState("リアルタイム推論を再開しました");
        } else {
            publishState("推論workerを再起動しています");
        }
    }

    /**
     * マイクPCMをWAVと推論workerへ振り分けます。
     * @param samples float PCM。例: {@code new float[8000]}
     * @param length 有効サンプル数。例: {@code 8000}
     */
    private void onAudioChunk(final float[] samples, final int length) {
        final RecordedAudioFileWriter writer = audioFileWriter;
        if (writer != null) {
            try {
                writer.append(samples, length);
            } catch (IOException e) {
                Log.e(TAG, "Audio recording write failed", e);
                closeAudioFile(false);
            }
        }
        final WhisperTranscriptionWorker worker = transcriptionWorker;
        if (worker != null && inferenceAccepting) {
            worker.submit(samples, length);
        }
    }

    /** @return 音声投入可能な推論workerを用意できた場合true。例: {@code true} */
    private boolean ensureInferenceAccepting() {
        if (transcriptionWorker != null && transcriptionWorker.isAlive()) {
            final boolean resumed = transcriptionWorker.resumeAudioSubmission();
            inferenceAlive = true;
            inferenceAccepting = resumed;
            return resumed;
        }
        if (transcriptionJsonWorker != null && transcriptionJsonWorker.isAlive()) {
            return false;
        }
        startInferencePipeline();
        return true;
    }

    /** 現在設定でリアルタイム推論workerとJSON workerを開始します。 */
    private void startInferencePipeline() {
        inferenceSessionId = newSessionId("live-" + inferenceSequence++);
        transcriptionWorker = new WhisperTranscriptionWorker(
                modelPath,
                vadModelPath,
                inferenceSessionId,
                settings
        );
        transcriptionJsonWorker = new TranscriptionJsonWorker(this, inferenceSessionId);
        whisperThreadStopped = false;
        jsonThreadStopped = false;
        inferenceAlive = true;
        inferenceAccepting = true;
        inferenceFailed = false;
        transcriptionJsonWorker.start();
        transcriptionWorker.start();
    }

    /** AudioRecord workerへ停止を要求します。 */
    private void requestRecordThreadStop() {
        if (recordWorker == null || !recordWorker.requestStop()) {
            finishRecordThread();
        }
    }

    /** JSON workerへ停止を要求します。 */
    private void requestJsonThreadStop() {
        if (transcriptionJsonWorker == null || !transcriptionJsonWorker.requestStop()) {
            jsonThreadStopped = true;
            transcriptionJsonWorker = null;
            finishIdleWorkIfPossible();
        }
    }

    /**
     * worker停止イベントを対応する状態へ反映します。
     * @param event 停止情報。例: {@code new ThreadStoppedEvent(...)}
     */
    private synchronized void onThreadStopped(final ThreadStoppedEvent event) {
        if ("Record".equals(event.owner()) && recordingSessionId != null
                && event.threadId().startsWith(recordingSessionId)) {
            finishRecordThread();
        } else if ("Whisper".equals(event.owner()) && inferenceSessionId != null
                && event.threadId().startsWith(inferenceSessionId)) {
            inferenceFailed |= event.hasError();
            whisperThreadStopped = true;
            transcriptionWorker = null;
            inferenceAlive = false;
            inferenceAccepting = false;
            requestJsonThreadStop();
        } else if ("Json".equals(event.owner()) && inferenceSessionId != null
                && event.threadId().startsWith(inferenceSessionId)) {
            jsonThreadStopped = true;
            transcriptionJsonWorker = null;
            finishIdleWorkIfPossible();
        }
        if (event.hasError()) {
            Log.w(TAG, event.owner() + " stopped with error: " + event.errorMessage());
        }
    }

    /** 録音ファイルを確定し、再開待ちまたは推論キュー排出へ進みます。 */
    private void finishRecordThread() {
        recordWorker = null;
        releaseMediaProjection();
        closeAudioFile(true);
        if (pendingRecordingStart) {
            startRecordingInternalAsync();
            return;
        }
        if (transcriptionWorker != null && transcriptionWorker.isAlive()) {
            inferenceAccepting = false;
            transcriptionWorker.requestStop();
        } else {
            finishIdleWorkIfPossible();
        }
    }

    /** 生存workerがなくなった時に再開、自動再推論、Service終了のいずれかへ進みます。 */
    private synchronized void finishIdleWorkIfPossible() {
        if (recording) {
            if (pendingInferenceResume && jsonThreadStopped) {
                pendingInferenceResume = false;
                startInferencePipeline();
                currentState = RecordTranscriptionState.Recording;
                publishState("リアルタイム推論を再開しました");
            } else {
                publishState("録音中（推論停止）");
            }
            return;
        }
        if (!whisperThreadStopped || !jsonThreadStopped) {
            return;
        }
        if (inferenceFailed) {
            retranscriptionQueue.clear();
        }
        if (pendingRecordingStart) {
            startRecordingInternalAsync();
            return;
        }
        startNextRetranscriptionOrStop();
    }

    /**
     * リアルタイム推論結果をJSON、通知、UI状態へ反映します。
     * @param event 推論結果。例: {@code WhisperTranscriptionEvent}
     */
    private void onTranscriptionEvent(final WhisperTranscriptionEvent event) {
        if (retranscriptionSessionId != null
                && retranscriptionSessionId.equals(event.sessionId())) {
            latestText = event.hasError()
                    ? "保存音声の再推論エラー: " + event.errorMessage()
                    : (event.text().isEmpty() ? "..." : event.text());
            notificationController.update(latestText, false);
            publishState("保存音声の再推論が完了しました");
            return;
        }
        if (inferenceSessionId == null || !inferenceSessionId.equals(event.sessionId())) {
            return;
        }
        final TranscriptionJsonWorker jsonWorker = transcriptionJsonWorker;
        if (jsonWorker != null) {
            jsonWorker.submit(event);
        }
        if (event.hasError()) {
            inferenceFailed = true;
            latestText = "Whisper エラー: " + event.errorMessage();
        } else {
            latestText = event.text().isEmpty() ? "..." : event.text();
            settingsStore.recordInference(event.modelKey(), event.processingTimeMs());
        }
        notificationController.update(latestText, recording);
        publishState(recording ? "バックグラウンド録音中" : "残り音声を推論中です");
    }

    /**
     * 設定がONなら録音単位のWAV保存先を開きます。
     * @param sessionId 録音ID。例: {@code "record-a1b2"}
     * @param sampleRate Hz。例: {@code 16000}
     * @return 保存不要または作成成功ならtrue。例: {@code true}
     */
    private boolean openAudioFile(final String sessionId, final int sampleRate) {
        if (!settings.audioRecordingEnabled()) {
            return true;
        }
        try {
            audioFileWriter = new RecordedAudioFileWriter(this, sessionId, sampleRate);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Audio recording create failed", e);
            publishState("音声記録ファイルを作成できませんでした: " + e.getMessage());
            return false;
        }
    }

    /**
     * WAVを確定し、指定時は自動再推論の待ち行列へ追加します。
     * @param enqueueRetranscription 正常な録音終了として再推論候補にする場合true。例: {@code true}
     */
    private synchronized void closeAudioFile(final boolean enqueueRetranscription) {
        final RecordedAudioFileWriter writer = audioFileWriter;
        audioFileWriter = null;
        if (writer == null) {
            return;
        }
        final File file = writer.getOutputFile();
        try {
            writer.close();
            if (file.length() <= 44) {
                if (!file.delete()) {
                    Log.w(TAG, "Empty recording file could not be deleted: " + file);
                }
                return;
            }
            if (enqueueRetranscription && settings != null
                    && settings.autoRetranscribeEnabled()) {
                retranscriptionQueue.offer(new RetranscriptionRequest(
                        file,
                        settings,
                        newSessionId("recorded-file")
                ));
            }
        } catch (IOException e) {
            Log.e(TAG, "Audio recording close failed", e);
        }
    }

    /** 保存音声の次の再推論を開始し、残件がなければServiceを終了します。 */
    private synchronized void startNextRetranscriptionOrStop() {
        if (recording || inferenceAlive || pendingRecordingStart) {
            if (pendingRecordingStart) {
                startRecordingInternalAsync();
            }
            return;
        }
        if (retranscriptionWorker != null && retranscriptionWorker.isAlive()) {
            return;
        }
        final RetranscriptionRequest request = retranscriptionQueue.poll();
        if (request == null) {
            publishState("録音と推論を終了しました");
            stopForegroundAndSelf();
            return;
        }
        publishState("保存音声を再推論しています");
        retranscriptionSessionId = request.sessionId;
        retranscriptionWorker = new WhisperFileTranscriptionWorker(
                this,
                FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        request.file
                ),
                request.sessionId,
                request.settings,
                this::onRetranscriptionComplete
        );
        if (!retranscriptionWorker.start()) {
            retranscriptionWorker = null;
            retranscriptionSessionId = null;
            startNextRetranscriptionOrStop();
        }
    }

    /**
     * 保存音声の再推論完了後に次の処理へ進みます。
     * @param sessionId 完了ID。例: {@code "recorded-file-a1b2"}
     * @param errorMessage 成功時は空文字。例: {@code ""}
     */
    private void onRetranscriptionComplete(final String sessionId, final String errorMessage) {
        synchronized (this) {
            retranscriptionWorker = null;
            retranscriptionSessionId = null;
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Log.w(TAG, "Recorded audio retranscription failed: " + errorMessage);
            }
            if (pendingRecordingStart) {
                startRecordingInternalAsync();
            } else {
                startNextRetranscriptionOrStop();
            }
        }
    }

    /**
     * アプリ音声入力時だけ、Foreground化後にMediaProjection tokenを実体化します。
     * @return 不要または準備成功ならtrue。例: {@code true}
     * @throws SecurityException 無効または再利用済みtokenをOSが拒否した場合は捕捉してfalseへ変換します
     */
    private boolean prepareMediaProjectionIfNeeded() {
        if (!requestedAudioSource.requiresAppCapture()) {
            return true;
        }
        if (projectionResultCode != Activity.RESULT_OK
                || projectionResultData == null
                || requestedCaptureTargetUid < 0) {
            return false;
        }
        try {
            final MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            if (manager == null) {
                return false;
            }
            mediaProjection = manager.getMediaProjection(
                    projectionResultCode, projectionResultData);
            projectionResultData = null;
            mediaProjectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    synchronized (BackgroundWhisperService.this) {
                        if (recording) {
                            publishState("アプリ音声のキャプチャ許可が終了しました");
                            requestStopRecording();
                        }
                    }
                }
            };
            mediaProjection.registerCallback(
                    mediaProjectionCallback,
                    new Handler(Looper.getMainLooper())
            );
            return true;
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "MediaProjection initialization failed", e);
            releaseMediaProjection();
            return false;
        }
    }

    /** MediaProjection callbackを解除し、現在のキャプチャ許可を返却します。 */
    private void releaseMediaProjection() {
        final MediaProjection projection = mediaProjection;
        final MediaProjection.Callback callback = mediaProjectionCallback;
        mediaProjection = null;
        mediaProjectionCallback = null;
        if (projection == null) {
            return;
        }
        if (callback != null) {
            projection.unregisterCallback(callback);
        }
        projection.stop();
    }

    /** @throws IOException WhisperまたはVADモデルをassetsから準備できない場合 */
    private void prepareModels() throws IOException {
        modelPath = MyUtils.prepareModelPath(this, settings.model().assetName());
        vadModelPath = MyUtils.prepareModelPath(this, WhisperVadConfig.MODEL_ASSET_NAME);
    }

    /** 録音開始失敗時、先に起動した推論workerを排出停止します。 */
    private void stopInferenceIfNotRecording() {
        if (!recording && transcriptionWorker != null && transcriptionWorker.isAlive()) {
            inferenceAccepting = false;
            transcriptionWorker.requestStop();
        }
    }

    /** @param message UIと通知へ伝える状態。例: {@code "録音中"} */
    private void publishState(final String message) {
        SystemEventHub.publish(new WhisperRecordingStateEvent(
                recordingSessionId,
                recording,
                !recording && inferenceAlive,
                inferenceAlive,
                inferenceAccepting,
                message == null ? "" : message,
                latestText == null ? "" : latestText,
                currentModelKey == null ? "" : currentModelKey
        ));
    }

    /** Foreground通知を外し、Serviceを終了します。 */
    private synchronized void stopForegroundAndSelf() {
        recording = false;
        inferenceAlive = false;
        inferenceAccepting = false;
        currentState = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /**
     * 重複しにくいセッションIDを作成します。
     * @param prefix 用途名。例: {@code "record"}
     * @return ID。例: {@code "record-1a-2b"}
     */
    @NonNull
    private static String newSessionId(@NonNull final String prefix) {
        return StringBufferBuilderPool.Join("-", prefix,
                Long.toHexString(System.currentTimeMillis()),
                Long.toHexString(System.nanoTime()));
    }

    private static final class RetranscriptionRequest {
        final File file;
        final WhisperSettings settings;
        final String sessionId;

        /**
         * 再推論待ちデータを作成します。
         * @param file WAVファイル。例: {@code new File("record.wav")}
         * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
         * @param sessionId 結果ID。例: {@code "recorded-file-a1b2"}
         */
        RetranscriptionRequest(
                @NonNull final File file,
                @NonNull final WhisperSettings settings,
                @NonNull final String sessionId
        ) {
            this.file = file;
            this.settings = settings;
            this.sessionId = sessionId;
        }
    }
}
