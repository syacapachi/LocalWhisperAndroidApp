package jp.ac.gifu_u.programmingjissen2.Record;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import Utils.StringPool.StringBufferBuilderPool;
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
    private final float[] audioBuffer;

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
        void onAudioChunk(final float[] samples, final int length);
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
            final int sampleRate,
            final int bufferSize,
            final String stopEventId,
            final AudioChunkListener listener
    ) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.stopEventId = stopEventId;
        this.listener = listener;
        this.audioBuffer = new float[Math.max(1, bufferSize / Float.BYTES)];
    }

    /**
     * AudioRecord 用のバッファサイズを作成します。
     *
     * @param sampleRate 録音サンプリングレート
     * @return AudioRecord 用バッファサイズ。失敗時は -1
     */
    public static int createBufferSize(final int sampleRate) {
        final int minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
        );

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, StringBufferBuilderPool.Join(
                    "",
                    "Invalid AudioRecord buffer size: ",
                    minBufferSize
            ));
            return -1;
        }

        final int halfSecondBytes = sampleRate * Float.BYTES / 2;
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

        audioRecord = createAudioRecord(sampleRate,bufferSize);
        if (audioRecord == null) {
            return false;
        }

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
        final Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * 録音スレッドに停止を要求します。
     */
    public synchronized boolean requestStop() {
        running = false;

        final Thread thread = workerThread;
        if (thread == null) {
            return false;
        }

        // スレッドを停止するメッセージを送ります。
        thread.interrupt();

        // AudioRecord.read() のブロックを解除するために録音を停止します
        if (audioRecord == null) { return true;}
        try {
            audioRecord.stop();
        } catch (IllegalStateException e) {
            Log.w(TAG, "AudioRecord stop failed", e);
        }
        return true;
    }

    /**
     * 録音 worker スレッド本体です。
     *
     * <p>AudioRecord を開始して PCM を読み続け、終了時に必ず停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();
        String stopErrorMessage = null;

        try {
            if (audioRecord == null) {
                throw new IllegalStateException("AudioRecord is null");
            }
            audioRecord.startRecording();
            while (running && !currentThread.isInterrupted()) {
                readNextAudioChunk();
            }
        } catch (Exception e) {
            Log.e(TAG, "Record thread error", e);
            stopErrorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
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
    @Nullable
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private static AudioRecord createAudioRecord(final int sampleRate,final int bufferSize) {
        final AudioRecord record = new AudioRecord(
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
     * AudioRecord から 1 チャンク分の PCM を読み、listener へ渡します。
     */
    private void readNextAudioChunk() {
        final int dataSize = audioRecord.read(
                audioBuffer,
                0,
                audioBuffer.length,
                AudioRecord.READ_BLOCKING
        );

        if (dataSize > 0) {
            listener.onAudioChunk(audioBuffer, dataSize);
        } else if (dataSize < 0) {
            Log.w(TAG, StringBufferBuilderPool.Join(
                    "",
                    "AudioRecord read error: ",
                    dataSize
            ));
        }
    }

    /**
     * 録音スレッドが停止したことをイベントとして通知します。
     *
     * @param thread 停止したスレッド
     * @param errorMessage エラー終了した場合のメッセージ
     */
    private void publishStoppedEvent(@NonNull Thread thread, String errorMessage) {
        SystemEventHub.publish(new ThreadStoppedEvent(
                stopEventId,
                "Record",
                thread.getName(),
                thread.isInterrupted(),
                errorMessage
        ));
    }

}
