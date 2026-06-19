package jp.ac.gifu_u.programmingjissen2;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperTranscriptionEvent;

/**
 * Whisper の文字起こし結果を別スレッドで JSON ファイルへ保存する worker です。
 */
public class TranscriptionJsonWorker implements Runnable {
    /** ログ出力用タグです。 */
    private static final String TAG = TranscriptionJsonWorker.class.getSimpleName();

    /** JSON 保存待ちイベントの最大数です。 */
    private static final int QUEUE_CAPACITY = 128;

    /** JSON ファイル保存先を取得するための Context です。 */
    private final Context context;

    /** 録音開始ごとに作る session ID です。 */
    private final String sessionId;

    /** この worker の停止イベントを識別する ID です。 */
    private final String stopEventId;

    /** Whisper 推論結果を JSON 保存スレッドへ渡すキューです。 */
    private final ArrayBlockingQueue<WhisperTranscriptionEvent> eventQueue =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /** worker スレッドの継続フラグです。 */
    private volatile boolean running;

    /** JSON 保存を実行する Java スレッドです。 */
    private Thread workerThread;

    /** 実際の JSON ファイル書き込みを行う writer です。 */
    private TranscriptionJsonWriter writer;

    /**
     * JSON 保存 worker を作成します。
     *
     * @param context ファイル保存先を取得するための Context
     * @param sessionId 録音 session ID
     */
    public TranscriptionJsonWorker(Context context, String sessionId) {
        this.context = context.getApplicationContext();
        this.sessionId = sessionId;
        this.stopEventId = StringBufferBuilderPool.Join("", sessionId, ":json");
    }

    /**
     * JSON 保存スレッドを開始します。
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        workerThread = new Thread(this, "TranscriptionJsonWorker");
        workerThread.start();
    }

    /**
     * この worker スレッドの停止イベントを待つための ID を返します。
     *
     * @return 停止イベント ID
     */
    public String getStopEventId() {
        return stopEventId;
    }

    /**
     * worker スレッドがまだ生きている場合 true を返します。
     *
     * @return worker スレッドが生存している場合 true
     */
    public boolean isAlive() {
        Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * JSON 保存対象の Whisper 推論イベントをキューへ追加します。
     *
     * @param event Whisper 推論結果イベント
     */
    public void submit(WhisperTranscriptionEvent event) {
        if (!running || event == null || event.hasError()) {
            return;
        }

        if (!eventQueue.offer(event)) {
            eventQueue.poll();
            eventQueue.offer(event);
            Log.w(TAG, "JSON queue is full. Dropped old transcription event.");
        }
    }

    /**
     * JSON 保存スレッドへ停止を要求します。
     *
     * @return 停止要求を出せた場合 true
     */
    public synchronized boolean requestStop() {
        running = false;

        Thread thread = workerThread;
        if (thread == null) {
            return false;
        }

        thread.interrupt();
        return true;
    }

    /**
     * JSON 保存 worker スレッド本体です。
     *
     * <p>キューに届いた文字起こしイベントを JSON に保存し、停止時に残りを保存してから
     * 停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        String stopErrorMessage = null;

        try {
            writer = new TranscriptionJsonWriter(context, sessionId);
            consumeEventsUntilStopped(currentThread);
        } catch (InterruptedException e) {
            currentThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "JSON worker error", e);
            stopErrorMessage = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
        } finally {
            drainQueuedEvents();
            finishWriter();
            running = false;
            workerThread = null;
            publishStoppedEvent(currentThread, stopErrorMessage);
        }
    }

    /**
     * 停止要求または interrupt まで JSON 保存イベントを消費します。
     *
     * @param currentThread worker 自身のスレッド
     * @throws InterruptedException キュー待機中に interrupt された場合
     */
    private void consumeEventsUntilStopped(Thread currentThread) throws InterruptedException {
        while (running && !currentThread.isInterrupted()) {
            WhisperTranscriptionEvent event = eventQueue.poll(200, TimeUnit.MILLISECONDS);
            if (event != null) {
                appendEvent(event);
            }
        }
    }

    /**
     * 停止時にキューへ残っているイベントをすべて JSON へ保存します。
     */
    private void drainQueuedEvents() {
        WhisperTranscriptionEvent event;
        while ((event = eventQueue.poll()) != null) {
            appendEvent(event);
        }
    }

    /**
     * 1 件の Whisper 推論イベントを JSON writer へ渡します。
     *
     * @param event Whisper 推論結果イベント
     */
    private void appendEvent(WhisperTranscriptionEvent event) {
        if (writer != null) {
            writer.append(event);
        }
    }

    /**
     * JSON writer に録音終了時刻を記録させます。
     */
    private void finishWriter() {
        if (writer == null) {
            return;
        }

        writer.finish();
        Log.d(TAG, StringBufferBuilderPool.Join(
                "",
                "Transcription JSON saved: ",
                writer.getOutputFile().getAbsolutePath()
        ));
        writer = null;
    }

    /**
     * JSON 保存スレッドが停止したことをイベントとして通知します。
     *
     * @param thread 停止したスレッド
     * @param errorMessage エラー終了した場合のメッセージ
     */
    private void publishStoppedEvent(Thread thread, String errorMessage) {
        SystemEventHub.publish(new ThreadStoppedEvent(
                stopEventId,
                "Json",
                thread.getName(),
                thread.isInterrupted(),
                errorMessage
        ));
    }
}
