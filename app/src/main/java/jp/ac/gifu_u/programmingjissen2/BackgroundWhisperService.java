package jp.ac.gifu_u.programmingjissen2;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.function.Consumer;

import Utils.MyUtils;
import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperRecordingStateEvent;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.Record.AudioRecordWorker;
import jp.ac.gifu_u.programmingjissen2.Transscripts.TranscriptionJsonWorker;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperSettingsStore;

/** バックグラウンドでも録音と Whisper 文字起こしを続ける foreground service です。 */
public class BackgroundWhisperService extends Service {
    private static final String TAG = BackgroundWhisperService.class.getSimpleName();

    public static final String ACTION_START = "jp.ac.gifu_u.programmingjissen2.whisper.START";
    public static final String ACTION_STOP = "jp.ac.gifu_u.programmingjissen2.whisper.STOP";

    private static final String CHANNEL_ID = "whisper_recording";
    private static final int NOTIFICATION_ID = 2100;

    private static volatile boolean active;
    private static volatile boolean recording;
    private static volatile boolean stopping;
    private static volatile String currentSessionId;
    private static volatile String latestText = "";
    private static volatile String currentModelKey = "";

    private WhisperSettingsStore settingsStore;
    private WhisperSettings settings;

    private AudioRecordWorker recordWorker;
    private WhisperTranscriptionWorker transcriptionWorker;
    private TranscriptionJsonWorker transcriptionJsonWorker;

    private volatile boolean recordThreadStopped = true;
    private volatile boolean whisperThreadStopped = true;
    private volatile boolean jsonThreadStopped = true;

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<ThreadStoppedEvent> threadStoppedListener = this::onThreadStopped;

    public static void startRecording(final Context context) {
        final Intent intent = new Intent(context, BackgroundWhisperService.class);
        intent.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stopRecording(final Context context) {
        final Intent intent = new Intent(context, BackgroundWhisperService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static boolean isRunning() {
        return active && recording;
    }

    public static boolean isStopping() {
        return stopping;
    }

    public static boolean isServiceActive() {
        return active;
    }

    public static String latestText() {
        return latestText;
    }

    public static String currentModelKey() {
        return currentModelKey;
    }

    /**
     * サービスが開始された時に呼ばれます。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        settingsStore = new WhisperSettingsStore(this);
        createNotificationChannel();
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            requestStopRecording();
            return START_NOT_STICKY;
        }

        startForegroundNotification("Whisper 録音を準備中", "モデルを準備しています");
        startRecordingInternalAsync();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.unsubscribe(ThreadStoppedEvent.class, threadStoppedListener);
        active = false;
        recording = false;
        stopping = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startRecordingInternalAsync() {
        if (recording || stopping) {
            updateNotification(latestText.isEmpty() ? "録音中" : latestText);
            publishState("録音中です");
            return;
        }

        new Thread(this::startRecordingInternal, "BackgroundWhisperStart").start();
    }

    private void startRecordingInternal() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            publishState("録音権限がありません");
            stopSelf();
            return;
        }

        settings = settingsStore.load();
        currentModelKey = settings.model().key();
        latestText = "";
        currentSessionId = StringBufferBuilderPool.Join(
                "-",
                Long.toHexString(System.currentTimeMillis()),
                Long.toHexString(System.nanoTime())
        );

        final int bufferSize = AudioRecordWorker.createBufferSize(WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE);
        if (bufferSize <= 0) {
            publishState("録音バッファを作成できませんでした");
            stopSelf();
            return;
        }

        String modelPath;
        try {
            modelPath = MyUtils.prepareModelPath(this, settings.model().assetName());
        } catch (IOException e) {
            Log.e(TAG, "Whisper model prepare failed", e);
            publishState(StringBufferBuilderPool.Join("", "モデル準備に失敗しました: ", e.getMessage()));
            stopSelf();
            return;
        }

        recordThreadStopped = false;
        whisperThreadStopped = false;
        jsonThreadStopped = false;

        transcriptionWorker = new WhisperTranscriptionWorker(
                modelPath,
                currentSessionId,
                settings
        );
        transcriptionJsonWorker = new TranscriptionJsonWorker(this, currentSessionId);
        recordWorker = new AudioRecordWorker(
                WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE,
                bufferSize,
                StringBufferBuilderPool.Join("", currentSessionId, ":record"),
                this::submitAudioToWhisper
        );

        transcriptionJsonWorker.start();
        transcriptionWorker.start();
        if (!recordWorker.start()) {
            recordThreadStopped = true;
            publishState("AudioRecord の初期化に失敗しました");
            requestWhisperThreadStop();
            return;
        }

        recording = true;
        stopping = false;
        updateNotification("録音中...");
        publishState("バックグラウンド録音中");
    }

    private void submitAudioToWhisper(final float[] samples, final int length) {
        final WhisperTranscriptionWorker worker = transcriptionWorker;
        if (worker != null) {
            worker.submit(samples, length);
        }
    }

    private void requestStopRecording() {
        if (stopping) {
            return;
        }

        if (!recording && recordWorker == null && transcriptionWorker == null) {
            stopForegroundAndSelf();
            return;
        }

        recording = false;
        stopping = true;
        updateNotification(latestText.isEmpty() ? "停止中..." : latestText);
        publishState("停止中...");
        requestRecordThreadStop();
        requestWhisperThreadStop();
        completeStopIfNeeded();
    }

    private void requestRecordThreadStop() {
        final AudioRecordWorker worker = recordWorker;
        if (worker == null || !worker.isAlive()) {
            recordThreadStopped = true;
            recordWorker = null;
            return;
        }

        if (!worker.requestStop()) {
            recordThreadStopped = true;
            recordWorker = null;
        }
    }

    private void requestWhisperThreadStop() {
        final WhisperTranscriptionWorker worker = transcriptionWorker;
        if (worker == null || !worker.isAlive()) {
            whisperThreadStopped = true;
            transcriptionWorker = null;
            requestJsonThreadStop();
            return;
        }

        if (!worker.requestStop()) {
            whisperThreadStopped = true;
            transcriptionWorker = null;
            requestJsonThreadStop();
        }
    }

    private void requestJsonThreadStop() {
        final TranscriptionJsonWorker worker = transcriptionJsonWorker;
        if (worker == null || !worker.isAlive()) {
            jsonThreadStopped = true;
            transcriptionJsonWorker = null;
            return;
        }

        if (!worker.requestStop()) {
            jsonThreadStopped = true;
            transcriptionJsonWorker = null;
        }
    }

    private void onThreadStopped(final ThreadStoppedEvent event) {
        if (currentSessionId == null || !event.threadId().startsWith(currentSessionId)) {
            return;
        }

        if ("Record".equals(event.owner())) {
            recordThreadStopped = true;
            recordWorker = null;
        } else if ("Whisper".equals(event.owner())) {
            whisperThreadStopped = true;
            transcriptionWorker = null;
            requestJsonThreadStop();
        } else if ("Json".equals(event.owner())) {
            jsonThreadStopped = true;
            transcriptionJsonWorker = null;
        }

        if (event.hasError()) {
            Log.w(TAG, StringBufferBuilderPool.Join(
                    "",
                    event.owner(),
                    " stopped with error: ",
                    event.errorMessage()
            ));
        }
        completeStopIfNeeded();
    }

    private void completeStopIfNeeded() {
        if (!stopping || !recordThreadStopped || !whisperThreadStopped || !jsonThreadStopped) {
            return;
        }

        publishState("録音を停止しました");
        stopForegroundAndSelf();
    }

    private void onTranscriptionEvent(final WhisperTranscriptionEvent event) {
        if (currentSessionId == null || !currentSessionId.equals(event.sessionId())) {
            return;
        }
        transcriptionJsonWorker.submit(event);

        if (event.hasError()) {
            latestText = StringBufferBuilderPool.Join("", "Whisper エラー: ", event.errorMessage());
        } else {
            latestText = event.text().isEmpty() ? "..." : event.text();
            settingsStore.recordInference(event.modelKey(), event.processingTimeMs());
        }
        updateNotification(latestText);
        publishState(recording ? "バックグラウンド録音中" : "停止中...");
    }

    private void publishState(String message) {
        SystemEventHub.publish(new WhisperRecordingStateEvent(
                currentSessionId,
                recording,
                stopping,
                message == null ? "" : message,
                latestText == null ? "" : latestText,
                currentModelKey == null ? "" : currentModelKey
        ));
    }

    /**
     * Android 26 未満用
     * 通知を作成します。
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Whisper 録音",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("バックグラウンド録音と Whisper 文字起こし");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Android26以上向け
     * 通知を作成
     * @param title タイトル
     * @param text 内容
     */
    private void startForegroundNotification(final String title, final String text) {
        final Notification notification = buildNotification(title, text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(final String text) {
        final NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification("Whisper 録音中", text));
        }
    }

    @NonNull
    private Notification buildNotification(final String title, final String text) {
        final Intent openIntent = new Intent(this, MainActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                pendingIntentFlags()
        );

        final Intent stopIntent = new Intent(this, BackgroundWhisperService.class);
        stopIntent.setAction(ACTION_STOP);
        final PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                pendingIntentFlags()
        );

        final String displayText = text == null || text.isEmpty() ? "最新の文字起こしはまだありません" : text;
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(displayText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(displayText))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "停止", stopPendingIntent)
                .build();
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void stopForegroundAndSelf() {
        recording = false;
        stopping = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }
}
