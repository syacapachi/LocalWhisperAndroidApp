package jp.ac.gifu_u.programmingjissen2.Record;

import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
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

    /** 使用するマイク・アプリ音声の組み合わせです。 */
    private final RecordingAudioSource audioSource;

    /** アプリ音声キャプチャの許可tokenです。 */
    private final MediaProjection mediaProjection;

    /** キャプチャ対象アプリのLinux UIDです。 */
    private final int captureTargetUid;

    /** 録音ループを継続するかどうかを表します。 */
    private volatile boolean running;

    /** AudioRecord の read ループを実行している Java スレッドです。 */
    private Thread workerThread;

    /** Android のマイク入力を読むための AudioRecord インスタンスです。 */
    private AudioRecord microphoneRecord;

    /** 対象アプリの再生音声を読むAudioRecordです。 */
    private AudioRecord playbackRecord;

    /** AudioRecord.read() が書き込む PCM バッファです。 */
    private final short[] audioBuffer;

    /** マイクとアプリ音声を混ぜる際の再生音声バッファです。 */
    private final short[] playbackBuffer;

    /**
     * AudioRecord から読み出した PCM チャンクを受け取る listener です。
     */
    public interface AudioChunkListener {
        /**
         * 録音スレッドから PCM チャンクが届いたときに呼ばれます。
         *
         * @param samples 16kHz・モノラル・PCM16。例: {@code new short[8000]}
         * @param length samples のうち有効な要素数
         */
        void onAudioChunk(final short[] samples, final int length);
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
        this(sampleRate, bufferSize, stopEventId, listener,
                RecordingAudioSource.MICROPHONE, null, -1);
    }

    /**
     * 指定した音声入力を読み取る録音workerを作成します。
     * @param sampleRate サンプリングレート。例: {@code 16000}
     * @param bufferSize AudioRecordバッファのbyte数。例: {@code 32000}
     * @param stopEventId 停止イベントID。例: {@code "record-a1:record"}
     * @param listener PCM通知先。例: {@code this::onAudioChunk}
     * @param audioSource 入力。例: {@code RecordingAudioSource.MICROPHONE_AND_APP}
     * @param mediaProjection キャプチャ許可。マイクのみならnull。例: {@code projection}
     * @param captureTargetUid 対象UID。マイクのみなら-1。例: {@code 10123}
     */
    public AudioRecordWorker(
            final int sampleRate,
            final int bufferSize,
            final String stopEventId,
            final AudioChunkListener listener,
            @NonNull final RecordingAudioSource audioSource,
            @Nullable final MediaProjection mediaProjection,
            final int captureTargetUid
    ) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.stopEventId = stopEventId;
        this.listener = listener;
        this.audioSource = audioSource;
        this.mediaProjection = mediaProjection;
        this.captureTargetUid = captureTargetUid;
        this.audioBuffer = new short[Math.max(1, bufferSize / Short.BYTES)];
        this.playbackBuffer = new short[this.audioBuffer.length];
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
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, StringBufferBuilderPool.Join(
                    "",
                    "Invalid AudioRecord buffer size: ",
                    minBufferSize
            ));
            return -1;
        }

        final int halfSecondBytes = sampleRate * Short.BYTES / 2;
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

        try {
            if (!createRequiredAudioRecords()) {
                releaseAudioRecords();
                return false;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "AudioRecord creation was rejected", e);
            releaseAudioRecords();
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
        stopAudioRecord(microphoneRecord);
        stopAudioRecord(playbackRecord);
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
            startRequiredAudioRecords();
            while (running && !currentThread.isInterrupted()) {
                readNextAudioChunk();
            }
        } catch (Exception e) {
            Log.e(TAG, "Record thread error", e);
            stopErrorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        } finally {
            releaseAudioRecords();
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
    private static AudioRecord createMicrophoneAudioRecord(
            final int sampleRate,
            final int bufferSize
    ) {
        final AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
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
     * 対象UIDだけに絞った再生音声AudioRecordを作成します。
     * @param sampleRate サンプリングレート。例: {@code 16000}
     * @param bufferSize byte数。例: {@code 32000}
     * @param projection MediaProjection許可。例: {@code projection}
     * @param targetUid 対象アプリUID。例: {@code 10123}
     * @return 初期化済みAudioRecord。失敗時null。例: {@code audioRecord}
     * @throws SecurityException RECORD_AUDIOまたはMediaProjection許可が無効な場合
     */
    @Nullable
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private static AudioRecord createPlaybackAudioRecord(
            final int sampleRate,
            final int bufferSize,
            @NonNull final MediaProjection projection,
            final int targetUid
    ) {
        final AudioPlaybackCaptureConfiguration configuration =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUid(targetUid)
                        .build();
        final AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        final AudioRecord record = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(configuration)
                .build();
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Playback AudioRecord initialization failed");
            record.release();
            return null;
        }
        return record;
    }

    /**
     * AudioRecord から 1 チャンク分の PCM を読み、listener へ渡します。
     */
    private void readNextAudioChunk() {
        final AudioRecord primary = audioSource == RecordingAudioSource.APP_CAPTURE
                ? playbackRecord : microphoneRecord;
        final int dataSize = primary.read(
                audioBuffer,
                0,
                audioBuffer.length,
                AudioRecord.READ_BLOCKING
        );

        if (dataSize > 0) {
            int outputSize = dataSize;
            if (audioSource == RecordingAudioSource.MICROPHONE_AND_APP) {
                final int playbackSize = playbackRecord.read(
                        playbackBuffer, 0, dataSize, AudioRecord.READ_BLOCKING);
                if (playbackSize < 0) {
                    Log.w(TAG, "Playback AudioRecord read error: " + playbackSize);
                    return;
                }
                outputSize = Math.min(dataSize, playbackSize);
                mixBuffers(outputSize);
            }
            listener.onAudioChunk(audioBuffer, outputSize);
        } else if (dataSize < 0) {
            Log.w(TAG, StringBufferBuilderPool.Join(
                    "",
                    "AudioRecord read error: ",
                    dataSize
            ));
        }
    }

    /** @return 必要なAudioRecordを全て初期化できた場合true。例: {@code true} */
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private boolean createRequiredAudioRecords() {
        if (audioSource != RecordingAudioSource.APP_CAPTURE) {
            microphoneRecord = createMicrophoneAudioRecord(sampleRate, bufferSize);
            if (microphoneRecord == null) {
                return false;
            }
        }
        if (audioSource.requiresAppCapture()) {
            if (mediaProjection == null || captureTargetUid < 0) {
                Log.e(TAG, "MediaProjection or capture target UID is missing");
                return false;
            }
            playbackRecord = createPlaybackAudioRecord(
                    sampleRate, bufferSize, mediaProjection, captureTargetUid);
            return playbackRecord != null;
        }
        return true;
    }

    /** 必要なAudioRecordを同時刻に録音開始状態へします。 */
    private void startRequiredAudioRecords() {
        if (microphoneRecord != null) {
            microphoneRecord.startRecording();
        }
        if (playbackRecord != null) {
            playbackRecord.startRecording();
        }
    }

    /**
     * マイクとアプリ音声を同じ音量比で加算し、PCM16範囲へ収めます。
     * @param length 混合するサンプル数。例: {@code 8000}
     */
    private void mixBuffers(final int length) {
        for (int index = 0; index < length; index++) {
            final int mixed = (audioBuffer[index] + playbackBuffer[index]) / 2;
            audioBuffer[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
        }
    }

    /** AudioRecordを停止し、readのブロックを解除します。@param record 停止対象。例: {@code microphoneRecord} */
    private static void stopAudioRecord(@Nullable final AudioRecord record) {
        if (record == null) {
            return;
        }
        try {
            record.stop();
        } catch (IllegalStateException e) {
            Log.w(TAG, "AudioRecord stop failed", e);
        }
    }

    /** 保持しているAudioRecordを全て解放します。 */
    private void releaseAudioRecords() {
        if (microphoneRecord != null) {
            microphoneRecord.release();
            microphoneRecord = null;
        }
        if (playbackRecord != null) {
            playbackRecord.release();
            playbackRecord = null;
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
