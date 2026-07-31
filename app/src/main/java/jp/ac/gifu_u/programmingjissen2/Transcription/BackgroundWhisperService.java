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
import jp.ac.gifu_u.programmingjissen2.Record.Buffer.PooledPcmChunk;
import jp.ac.gifu_u.programmingjissen2.Record.RecordTranscriptionState;
import jp.ac.gifu_u.programmingjissen2.Record.RecordedAudioFileWriter;
import jp.ac.gifu_u.programmingjissen2.Record.RecordingAudioSource;
import jp.ac.gifu_u.programmingjissen2.Record.WhisperFileTranscriptionWorker;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.ModelPathResolver;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsStore;
import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.TranscriptionJsonWorker;

/** バックグラウンド録音、リアルタイム推論、録音後再推論を管理するForeground Serviceです。 */
public class BackgroundWhisperService extends Service {
    private static final String TAG = BackgroundWhisperService.class.getSimpleName();

    public static final String ACTION_START = "jp.ac.gifu_u.programmingjissen2.whisper.START";
    public static final String ACTION_STOP = "jp.ac.gifu_u.programmingjissen2.whisper.STOP";
    public static final String ACTION_PAUSE_RECORDING =
            "jp.ac.gifu_u.programmingjissen2.whisper.PAUSE_RECORDING";
    public static final String ACTION_RESUME_RECORDING =
            "jp.ac.gifu_u.programmingjissen2.whisper.RESUME_RECORDING";
    public static final String ACTION_STOP_INFERENCE =
            "jp.ac.gifu_u.programmingjissen2.whisper.STOP_INFERENCE";
    public static final String ACTION_RESUME_INFERENCE =
            "jp.ac.gifu_u.programmingjissen2.whisper.RESUME_INFERENCE";
    public static final String ACTION_TRANSCRIBE_FILE =
            "jp.ac.gifu_u.programmingjissen2.whisper.TRANSCRIBE_FILE";
    public static final String ACTION_STOP_FILE_TRANSCRIPTION =
            "jp.ac.gifu_u.programmingjissen2.whisper.STOP_FILE_TRANSCRIPTION";
    private static final String EXTRA_AUDIO_SOURCE = "audioSource";
    private static final String EXTRA_PROJECTION_RESULT_CODE = "projectionResultCode";
    private static final String EXTRA_PROJECTION_DATA = "projectionData";

    private static volatile boolean active;
    private static volatile boolean sessionActive;
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
    private String recordingTimeData;
    private String pendingRecordingTimeData;
    private String inferenceSessionId;
    private String retranscriptionSessionId;
    private int nextInferenceSequence;
    private long sessionRecordedSamples;

    private AudioRecordWorker recordWorker;
    private WhisperTranscriptionWorker transcriptionWorker;
    private TranscriptionJsonWorker transcriptionJsonWorker;
    private volatile RecordedAudioFileWriter audioFileWriter;
    private WhisperFileTranscriptionWorker retranscriptionWorker;
    private RecordingAudioSource requestedAudioSource = RecordingAudioSource.MICROPHONE;
    private int projectionResultCode = Activity.RESULT_CANCELED;
    private Intent projectionResultData;
    private MediaProjection mediaProjection;
    private MediaProjection.Callback mediaProjectionCallback;

    private boolean pendingRecordingStart;
    private boolean pendingRecordingResume;
    private boolean pendingInferenceResume;
    private boolean sessionEndRequested;
    private boolean inferenceFailed;
    private volatile boolean destroyed;
    private boolean whisperThreadStopped = true;
    private boolean jsonThreadStopped = true;
    private final ArrayDeque<RetranscriptionRequest> retranscriptionQueue = new ArrayDeque<>();

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<ThreadStoppedEvent> threadStoppedListener = this::onThreadStopped;

    /** @param context 開始要求元。例: {@code activity} */
    public static void startRecording(final Context context) {
        startRecording(context, RecordingAudioSource.MICROPHONE,
                Activity.RESULT_CANCELED, null);
    }

    /**
     * 録音入力とMediaProjection許可を指定してForeground Serviceを開始します。
     * @param context 開始要求元。例: {@code activity}
     * @param source 音声入力。例: {@code RecordingAudioSource.APP_CAPTURE}
     * @param resultCode MediaProjection結果。例: {@code Activity.RESULT_OK}
     * @param projectionData MediaProjection token。マイクのみならnull。例: {@code resultIntent}
     */
    public static void startRecording(
            @NonNull final Context context,
            @NonNull final RecordingAudioSource source,
            final int resultCode,
            @Nullable final Intent projectionData
    ) {
        final Intent intent = new Intent(context, BackgroundWhisperService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_AUDIO_SOURCE, source.name())
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

    /** @param context 一時停止要求元。例: {@code activity} */
    public static void pauseRecording(final Context context) {
        sendAction(context, ACTION_PAUSE_RECORDING, false);
    }

    /** @param context 同一セッションの録音再開要求元。例: {@code activity} */
    public static void resumeRecording(final Context context) {
        sendAction(context, ACTION_RESUME_RECORDING, false);
    }

    /** @param context 推論停止要求元。例: {@code activity} */
    public static void stopInference(final Context context) {
        sendAction(context, ACTION_STOP_INFERENCE, false);
    }

    /** @param context 推論再開要求元。例: {@code activity} */
    public static void resumeInference(final Context context) {
        sendAction(context, ACTION_RESUME_INFERENCE, false);
    }

    /**
     * 選択音声のファイル文字起こしをForeground Serviceへ依頼します。
     * @param context 開始要求元。例: {@code activity}
     * @param uri 読み取り可能な音声URI。例: {@code content://media/1}
     * @return 開始Intentを送信した場合true。例: {@code true}
     * @throws NullPointerException uriがnullの場合
     */
    public static boolean transcribeAudioFile(
            @NonNull final Context context,
            @NonNull final android.net.Uri uri
    ) {
        if (active) {
            return false;
        }
        final Intent intent = new Intent(context, BackgroundWhisperService.class)
                .setAction(ACTION_TRANSCRIBE_FILE)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ContextCompat.startForegroundService(context, intent);
        return true;
    }

    public static boolean isRunning() { return active && recording; }
    public static boolean isSessionActive() { return active && sessionActive; }
    public static boolean isInferenceAlive() { return active && inferenceAlive; }
    public static boolean isInferenceAccepting() { return active && inferenceAccepting; }
    public static boolean isStopping() { return active && !sessionActive && inferenceAlive; }
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
        destroyed = false;
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
        } else if (ACTION_PAUSE_RECORDING.equals(action)) {
            requestPauseRecording();
        } else if (ACTION_RESUME_RECORDING.equals(action)) {
            requestResumeRecording();
        } else if (ACTION_STOP_FILE_TRANSCRIPTION.equals(action)) {
            requestStopFileTranscription();
        } else if (ACTION_STOP_INFERENCE.equals(action)) {
            requestStopInference();
        } else if (ACTION_RESUME_INFERENCE.equals(action)) {
            requestResumeInference();
        } else if (ACTION_TRANSCRIBE_FILE.equals(action)) {
            notificationController.startFileTranscription(
                    "音声ファイルを読み込み中",
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
            enqueueSelectedFile(intent);
        } else {
            readRecordingRequest(intent);
            if (!sessionActive) {
                pendingRecordingTimeData = newSessionTimeData();
                nextInferenceSequence = 0;
                sessionRecordedSamples = 0L;
            }
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
        destroyed = true;
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.unsubscribe(ThreadStoppedEvent.class, threadStoppedListener);
        closeAudioFile(false);
        if (retranscriptionWorker != null) {
            retranscriptionWorker.requestStop();
            retranscriptionWorker = null;
        }
        retranscriptionQueue.clear();
        releaseMediaProjection();
        active = false;
        sessionActive = false;
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
     * 録音開始Intentから入力とMediaProjection結果を読み込みます。
     * @param intent 開始Intent。例: {@code new Intent().putExtra("audioSource", "APP_CAPTURE")}
     */
    @SuppressWarnings("deprecation")
    private void readRecordingRequest(@Nullable final Intent intent) {
        if (intent == null) {
            requestedAudioSource = RecordingAudioSource.MICROPHONE;
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
        StringBufferBuilderPool.NewThreadWithPoolCleanup(
                this::startRecordingInternal,
                "BackgroundWhisperStart"
        ).start();
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

        if (pendingRecordingTimeData == null) {
            pendingRecordingTimeData = newSessionTimeData();
        }
        final String sessionTimeData = pendingRecordingTimeData;
        recordingSessionId = sessionId("session", sessionTimeData);
        if (!ensureInferenceAccepting(sessionTimeData)) {
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
        sessionEndRequested = false;
        sessionActive = true;
        recordingTimeData = sessionTimeData;
        pendingRecordingTimeData = null;
        recordWorker = new AudioRecordWorker(
                sampleRate,
                bufferSize,
                recordingSessionId + ":record",
                this::onAudioChunk,
                requestedAudioSource,
                mediaProjection
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

    /** セッションを終了し、録音停止後に推論キューと各writerを確定します。 */
    private synchronized void requestStopRecording() {
        if (!sessionActive) {
            return;
        }
        sessionActive = false;
        sessionEndRequested = true;
        recording = false;
        pendingRecordingStart = false;
        pendingRecordingResume = false;
        pendingInferenceResume = false;
        inferenceAccepting = false;
        currentState = RecordTranscriptionState.StopRecord;
        publishState("セッション終了: 残り音声を推論中です");
        if (recordWorker != null && recordWorker.isAlive()) {
            requestRecordThreadStop();
        } else {
            finishRecordThread();
        }
    }

    /** 録音workerだけを停止し、推論workerと同一セッションのwriterを維持します。 */
    private synchronized void requestPauseRecording() {
        if (!sessionActive || !recording) {
            return;
        }
        recording = false;
        pendingRecordingResume = false;
        updateSessionState();
        publishState("録音を一時停止しました");
        requestRecordThreadStop();
    }

    /** 同一セッション・同一WAVへ追記するAudioRecord workerを再生成します。 */
    private synchronized void requestResumeRecording() {
        if (!sessionActive || recording) {
            return;
        }
        if (recordWorker != null && recordWorker.isAlive()) {
            pendingRecordingResume = true;
            publishState("録音workerの停止後に再開します");
            return;
        }
        startRecordingWorkerForSession();
    }

    /**
     * 保持中の入力設定・MediaProjection・writerを使ってAudioRecord workerだけを再生成します。
     * 引数と戻り値はありません。AudioRecord初期化失敗時は一時停止状態を維持して通知します。
     */
    private void startRecordingWorkerForSession() {
        final int sampleRate = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;
        final int bufferSize = AudioRecordWorker.createBufferSize(sampleRate);
        if (bufferSize <= 0) {
            publishState("録音バッファを再作成できませんでした");
            return;
        }
        pendingRecordingResume = false;
        recordWorker = new AudioRecordWorker(
                sampleRate,
                bufferSize,
                recordingSessionId + ":record",
                this::onAudioChunk,
                requestedAudioSource,
                mediaProjection
        );
        if (!recordWorker.start()) {
            recordWorker = null;
            updateSessionState();
            publishState("AudioRecord の再初期化に失敗しました");
            return;
        }
        recording = true;
        updateSessionState();
        notificationController.update("録音を再開しました", true);
        publishState("同じセッションで録音を再開しました");
    }

    /**
     * ファイル文字起こしを停止し、待機中のファイル要求も破棄します。
     * 戻り値と例外はありません。native推論中は現在の窓終了後に停止します。
     */
    private synchronized void requestStopFileTranscription() {
        retranscriptionQueue.clear();
        pendingRecordingStart = false;
        if (retranscriptionWorker != null && retranscriptionWorker.requestStop()) {
            latestText = "ファイル文字起こしを停止しています";
            notificationController.updateFileTranscription(latestText);
            publishState(latestText);
            return;
        }
        retranscriptionSessionId = null;
        inferenceAlive = false;
        currentState = null;
        publishState("ファイル文字起こしを停止しました");
        stopForegroundAndSelf();
    }

    /** 録音を続けたまま推論への投入を止め、推論workerを排出停止させます。 */
    private synchronized void requestStopInference() {
        if (!sessionActive || transcriptionWorker == null) {
            return;
        }
        pendingInferenceResume = false;
        inferenceAccepting = false;
        updateSessionState();
        transcriptionWorker.requestStop();
        publishState("推論停止: キュー排出後にworkerを停止します");
    }

    /** 録音中の推論投入を再開し、必要なら推論workerを再作成します。 */
    private synchronized void requestResumeInference() {
        if (!sessionActive) {
            publishState("セッション継続中のみ推論を再開できます");
            return;
        }
        pendingInferenceResume = true;
        if (ensureInferenceAccepting(recordingTimeData)) {
            pendingInferenceResume = false;
            updateSessionState();
            publishState("リアルタイム推論を再開しました");
        } else {
            publishState("推論workerを再起動しています");
        }
    }

    /**
     * マイクPCMをWAVと推論workerへ振り分けます。
     * @param chunk 所有権付きDirect PCM16。例: {@code pool.acquire()}
     * @throws RuntimeException 保存・投入の予期しない失敗時。未移譲チャンクは必ず返却します。
     */
    private void onAudioChunk(@NonNull final PooledPcmChunk chunk) {
        boolean transferred = false;
        try {
            synchronized (this) {
                sessionRecordedSamples += chunk.sampleCount();
            }
            final RecordedAudioFileWriter writer = audioFileWriter;
            if (writer != null) {
                try {
                    writer.append(chunk.bytes(), 0, chunk.sampleCount());
                } catch (IOException e) {
                    Log.e(TAG, "Audio recording write failed", e);
                    closeAudioFile(false);
                }
            }
            final WhisperTranscriptionWorker worker = transcriptionWorker;
            if (worker != null && inferenceAccepting) {
                transferred = worker.submit(chunk);
            }
        } finally {
            if (!transferred) {
                chunk.close();
            }
        }
    }

    /**
     * 指定時刻データの推論workerを用意します。
     * @param sessionTimeData 録音と共有する時刻データ。例: {@code "19f99e5b391-317248c70b73"}
     * @return 音声投入可能な推論workerを用意できた場合true。例: {@code true}
     */
    private boolean ensureInferenceAccepting(@NonNull final String sessionTimeData) {
        if (transcriptionWorker != null && transcriptionWorker.isAlive()) {
            if (inferenceSessionId == null
                    || !inferenceSessionId.endsWith("-" + sessionTimeData)) {
                inferenceAccepting = false;
                transcriptionWorker.requestStop();
                return false;
            }
            final boolean resumed = transcriptionWorker.resumeAudioSubmission();
            inferenceAlive = true;
            inferenceAccepting = resumed;
            return resumed;
        }
        startInferencePipeline(sessionTimeData);
        return true;
    }

    /**
     * 現在設定と録音共通の時刻データで推論workerを開始します。
     * @param sessionTimeData 録音と共有する時刻データ。例: {@code "19f99e5b391-317248c70b73"}
     */
    private void startInferencePipeline(@NonNull final String sessionTimeData) {
        inferenceSessionId = recordingSessionId == null
                ? sessionId("session", sessionTimeData)
                : recordingSessionId;
        transcriptionWorker = new WhisperTranscriptionWorker(
                modelPath,
                vadModelPath,
                inferenceSessionId,
                WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE,
                settings,
                nextInferenceSequence,
                sessionRecordedSamples
        );
        if (transcriptionJsonWorker == null) {
            transcriptionJsonWorker = new TranscriptionJsonWorker(
                    this,
                    inferenceSessionId,
                    settings
            );
            jsonThreadStopped = false;
            transcriptionJsonWorker.start();
        }
        whisperThreadStopped = false;
        inferenceAlive = true;
        inferenceAccepting = true;
        inferenceFailed = false;
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
    private synchronized void onThreadStopped(@NonNull final ThreadStoppedEvent event) {
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
            if (sessionEndRequested) {
                requestJsonThreadStop();
            } else if (pendingInferenceResume && sessionActive) {
                pendingInferenceResume = false;
                startInferencePipeline(recordingTimeData);
                updateSessionState();
                publishState("同じセッションで推論を再開しました");
            } else {
                updateSessionState();
                publishState("推論を一時停止しました");
            }
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

    /** AudioRecord停止後、一時停止なら資源を保持し、セッション終了ならwriter確定へ進みます。 */
    private void finishRecordThread() {
        recordWorker = null;
        if (!sessionEndRequested) {
            if (pendingRecordingResume && sessionActive) {
                startRecordingWorkerForSession();
            } else {
                updateSessionState();
                publishState("録音一時停止中");
            }
            return;
        }
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
            requestJsonThreadStop();
        }
    }

    /** 生存workerがなくなった時に再開、自動再推論、Service終了のいずれかへ進みます。 */
    private synchronized void finishIdleWorkIfPossible() {
        if (sessionActive) {
            updateSessionState();
            publishState(inferenceAccepting ? "セッション継続中" : "推論一時停止中");
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
                    ? "ファイル文字起こしエラー: " + event.errorMessage()
                    : (event.text().isEmpty() ? "..." : event.text());
            if (!event.hasError()) {
                settingsStore.recordInference(event.modelKey(), event.processingTimeMs());
            }
            notificationController.updateFileTranscription(latestText);
            publishState(event.finalResult()
                    ? "ファイル文字起こしが完了しました"
                    : "ファイル文字起こし中です");
            return;
        }
        if (inferenceSessionId == null || !inferenceSessionId.equals(event.sessionId())) {
            return;
        }
        synchronized (this) {
            nextInferenceSequence = Math.max(nextInferenceSequence, event.sequence() + 1);
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
        notificationController.update(latestText, sessionActive);
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
                        FileProvider.getUriForFile(
                                this,
                                getPackageName() + ".fileprovider",
                                file
                        ),
                        settings,
                        sessionId(
                                "recorded-file",
                                recordingTimeData == null
                                        ? newSessionTimeData() : recordingTimeData
                        ),
                        "保存音声"
                ));
            }
        } catch (IOException e) {
            Log.e(TAG, "Audio recording close failed", e);
        }
    }

    /**
     * Service開始IntentのURIをファイル文字起こし待ち行列へ追加します。
     * @param intent ACTION_TRANSCRIBE_FILE Intent。例: {@code new Intent().setData(uri)}
     * 戻り値と例外はなく、不正要求時はエラー状態を通知してServiceを終了します。
     */
    private synchronized void enqueueSelectedFile(@Nullable final Intent intent) {
        final android.net.Uri uri = intent == null ? null : intent.getData();
        if (uri == null) {
            latestText = "音声ファイル文字起こし要求が不正です";
            publishState(latestText);
            stopForegroundAndSelf();
            return;
        }
        final WhisperSettings requestSettings = settingsStore.load();
        final String fileSessionId = sessionId("file", newSessionTimeData());
        retranscriptionQueue.offer(new RetranscriptionRequest(
                uri,
                requestSettings,
                fileSessionId,
                "音声ファイル"
        ));
        startNextRetranscriptionOrStop();
    }

    /** 保存音声の次の再推論を開始し、残件がなければServiceを終了します。 */
    private synchronized void startNextRetranscriptionOrStop() {
        if (sessionActive || inferenceAlive || pendingRecordingStart) {
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
        currentState = RecordTranscriptionState.FileTranscribing;
        inferenceAlive = true;
        inferenceAccepting = false;
        currentModelKey = request.settings.fileTranscription().model().key();
        notificationController.startFileTranscription(
                request.label + "を文字起こししています",
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );
        publishState(request.label + "をバックグラウンドで文字起こししています");
        retranscriptionSessionId = request.sessionId;
        retranscriptionWorker = new WhisperFileTranscriptionWorker(
                this,
                request.uri,
                request.sessionId,
                request.settings,
                this::onRetranscriptionComplete
        );
        if (!retranscriptionWorker.start()) {
            retranscriptionWorker = null;
            retranscriptionSessionId = null;
            inferenceAlive = false;
            currentState = null;
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
            if (destroyed) {
                return;
            }
            retranscriptionWorker = null;
            retranscriptionSessionId = null;
            inferenceAlive = false;
            inferenceAccepting = false;
            currentState = null;
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
                || projectionResultData == null) {
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
                        if (sessionActive) {
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

    /** @throws IOException 選択したCTranslate2モデルまたはSilero VADをassetsから準備できない場合 */
    private void prepareModels() throws IOException {
        modelPath = ModelPathResolver.resolve(this, settings.model());
        vadModelPath = settings.vadEnabled()
                ? MyUtils.prepareModelPath(this, WhisperVadConfig.MODEL_ASSET_NAME)
                : null;
    }

    /** 録音開始失敗時、先に起動した推論workerを排出停止します。 */
    private void stopInferenceIfNotRecording() {
        sessionActive = false;
        sessionEndRequested = true;
        if (!recording && transcriptionWorker != null && transcriptionWorker.isAlive()) {
            inferenceAccepting = false;
            transcriptionWorker.requestStop();
        } else {
            requestJsonThreadStop();
        }
    }

    /**
     * セッション・録音・推論の独立したフラグから画面表示用状態を更新します。
     * 引数と戻り値はなく、状態の組み合わせは例外を送出しません。
     */
    private void updateSessionState() {
        if (!sessionActive) {
            currentState = sessionEndRequested ? RecordTranscriptionState.StopRecord : null;
        } else if (recording && inferenceAccepting) {
            currentState = RecordTranscriptionState.Recording;
        } else if (!recording && inferenceAccepting) {
            currentState = RecordTranscriptionState.RecordingPaused;
        } else if (recording) {
            currentState = RecordTranscriptionState.InferencePaused;
        } else {
            currentState = RecordTranscriptionState.RecordingAndInferencePaused;
        }
    }

    /** @param message UIと通知へ伝える状態。例: {@code "録音中"} */
    private void publishState(final String message) {
        SystemEventHub.publish(new WhisperRecordingStateEvent(
                recordingSessionId,
                sessionActive,
                recording,
                !sessionActive && inferenceAlive,
                inferenceAlive,
                inferenceAccepting,
                message == null ? "" : message,
                latestText == null ? "" : latestText,
                currentModelKey == null ? "" : currentModelKey
        ));
    }

    /** Foreground通知を外し、Serviceを終了します。 */
    private synchronized void stopForegroundAndSelf() {
        sessionActive = false;
        recording = false;
        inferenceAlive = false;
        inferenceAccepting = false;
        currentState = null;
        recordingSessionId = null;
        recordingTimeData = null;
        pendingRecordingTimeData = null;
        sessionEndRequested = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /**
     * 録音と文字起こしで共有する重複しにくい時刻データを作成します。
     * @return 時刻データ。例: {@code "19f99e5b391-317248c70b73"}
     */
    @NonNull
    private static String newSessionTimeData() {
        return StringBufferBuilderPool.Join("-",
                Long.toHexString(System.currentTimeMillis()),
                Long.toHexString(System.nanoTime()));
    }

    /**
     * 用途名と共通時刻データからセッションIDを作成します。
     * @param prefix 用途名。例: {@code "live-0"}
     * @param timeData 共通時刻データ。例: {@code "19f99e5b391-317248c70b73"}
     * @return セッションID。例: {@code "live-0-19f99e5b391-317248c70b73"}
     */
    @NonNull
    private static String sessionId(
            @NonNull final String prefix,
            @NonNull final String timeData
    ) {
        return StringBufferBuilderPool.Join("-", prefix, timeData);
    }

    private static final class RetranscriptionRequest {
        final android.net.Uri uri;
        final WhisperSettings settings;
        final String sessionId;
        final String label;

        /**
         * 再推論待ちデータを作成します。
         * @param uri 音声URI。例: {@code content://media/1}
         * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
         * @param sessionId 結果ID。例: {@code "recorded-file-a1b2"}
         * @param label 通知表示名。例: {@code "保存音声"}
         */
        RetranscriptionRequest(
                @NonNull final android.net.Uri uri,
                @NonNull final WhisperSettings settings,
                @NonNull final String sessionId,
                @NonNull final String label
        ) {
            this.uri = uri;
            this.settings = settings;
            this.sessionId = sessionId;
            this.label = label;
        }
    }
}
