package jp.ac.gifu_u.programmingjissen2;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;

/**
 * AudioRecord の生成、録音ループ、停止イベント通知を担当する worker です。
 */
public class AudioRecordWorker implements Runnable {
    /** ログ出力用タグです。 */
    private static final String TAG = AudioRecordWorker.class.getSimpleName();

    /** AudioRecord で録音するサンプリングレートです。 */
    private final int sampleRate;

    /** AudioRecord に渡すバッファサイズです。byte 単位。 */
    private final int bufferSize;

    /** 録音スレッド停止イベントを識別する ID です。 */
    private final String stopEventId;

    /** 録音した PCM チャンクを受け取る callback です。 */
    private final AudioChunkListener listener;

    /** 録音ループを継続するかどうかを表します。 */
    private volatile boolean running;

    /** AudioRecord の read ループを実行している Java スレッドです。 */
    private Thread workerThread;

    /** Android のマイク入力を読むための AudioRecord インスタンスです。 */
    private AudioRecord audioRecord;

    /** AudioRecord.read() が書き込む PCM バッファです。 */
    private float[] audioBuffer;

    /**
     * AudioRecord から読み出した PCM チャンクを受け取る listener です。
     */
    public interface AudioChunkListener {
        /**
         * 録音スレッドから PCM チャンクが届いたときに呼ばれます。
         *
         * @param samples 16kHz・モノラル・float PCM
         * @param length samples のうち有効な要素数
         */
        void onAudioChunk(float[] samples, int length);
    }

    /**
     * AudioRecord の録音 worker を作成します。
     *
     * @param sampleRate 録音サンプリングレート
     * @param bufferSize AudioRecord に渡すバッファサイズ
     * @param stopEventId 停止イベント ID
     * @param listener PCM チャンクの通知先
     */
    public AudioRecordWorker(
            int sampleRate,
            int bufferSize,
            String stopEventId,
            AudioChunkListener listener
    ) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.stopEventId = stopEventId;
        this.listener = listener;
    }

    /**
     * AudioRecord 用のバッファサイズを作成します。
     *
     * @param sampleRate 録音サンプリングレート
     * @return AudioRecord 用バッファサイズ。失敗時は -1
     */
    public static int createBufferSize(int sampleRate) {
        int minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid AudioRecord buffer size: " + minBufferSize);
            return -1;
        }

        int halfSecondBytes = sampleRate * Float.BYTES / 2;
        return Math.max(minBufferSize * 2, halfSecondBytes);
    }

    /**
     * 録音スレッドを開始します。
     *
     * @return 開始できた場合 true
     */
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    public synchronized boolean start() {
        if (running) {
            return true;
        }

        audioRecord = createAudioRecord();
        if (audioRecord == null) {
            return false;
        }

        audioBuffer = new float[Math.max(1, bufferSize / Float.BYTES)];
        running = true;
        workerThread = new Thread(this, "AudioRecordThread");
        workerThread.start();
        return true;
    }

    /**
     * この録音スレッドの停止イベントを待つための ID を返します。
     */
    public String getStopEventId() {
        return stopEventId;
    }

    /**
     * スレッドがまだ生きている場合 true を返します。
     */
    public boolean isAlive() {
        Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * 録音スレッドに停止を要求します。
     */
    public synchronized boolean requestStop() {
        running = false;

        Thread thread = workerThread;
        if (thread == null) {
            return false;
        }

        thread.interrupt();
        stopAudioRecord();
        return true;
    }

    /**
     * 録音 worker スレッド本体です。
     *
     * <p>AudioRecord を開始して PCM を読み続け、終了時に必ず停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        String stopErrorMessage = null;

        try {
            startAudioRecord();
            readAudioUntilStopped(currentThread);
        } catch (Exception e) {
            Log.e(TAG, "Record thread error", e);
            stopErrorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            releaseAudioRecord();
            running = false;
            workerThread = null;
            publishStoppedEvent(currentThread, stopErrorMessage);
        }
    }

    /**
     * AudioRecord インスタンスを作成します。
     *
     * @return 初期化済み AudioRecord。失敗時は null
     */
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private AudioRecord createAudioRecord() {
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize
        );

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            record.release();
            return null;
        }

        return record;
    }

    /**
     * AudioRecord の録音を開始します。
     */
    private void startAudioRecord() {
        if (audioRecord == null) {
            throw new IllegalStateException("AudioRecord is null");
        }
        audioRecord.startRecording();
    }

    /**
     * stop 要求または interrupt まで録音チャンクを読み続けます。
     *
     * @param currentThread 録音 worker 自身のスレッド
     */
    private void readAudioUntilStopped(Thread currentThread) {
        while (running && !currentThread.isInterrupted()) {
            readNextAudioChunk();
        }
    }

    /**
     * AudioRecord から 1 チャンク分の PCM を読み、listener へ渡します。
     */
    private void readNextAudioChunk() {
        int dataSize = audioRecord.read(
                audioBuffer,
                0,
                audioBuffer.length,
                AudioRecord.READ_BLOCKING
        );

        if (dataSize > 0) {
            listener.onAudioChunk(audioBuffer, dataSize);
        } else if (dataSize < 0) {
            Log.w(TAG, "AudioRecord read error: " + dataSize);
        }
    }

    /**
     * AudioRecord.read() のブロックを解除するために録音を停止します。
     */
    private void stopAudioRecord() {
        if (audioRecord == null) {
            return;
        }

        try {
            audioRecord.stop();
        } catch (IllegalStateException e) {
            Log.w(TAG, "AudioRecord stop failed", e);
        }
    }

    /**
     * AudioRecord を解放します。
     */
    private void releaseAudioRecord() {
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
    }

    /**
     * 録音スレッドが停止したことをイベントとして通知します。
     *
     * @param thread 停止したスレッド
     * @param errorMessage エラー終了した場合のメッセージ
     */
    private void publishStoppedEvent(Thread thread, String errorMessage) {
        SystemEventHub.publish(new ThreadStoppedEvent(
                stopEventId,
                "Record",
                thread.getName(),
                thread.isInterrupted(),
                errorMessage
        ));
    }
}
