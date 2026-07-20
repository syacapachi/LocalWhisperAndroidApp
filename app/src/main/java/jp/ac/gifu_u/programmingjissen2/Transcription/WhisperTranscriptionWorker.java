package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import Utils.Pool.ObjectPool;
import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/**
 * 録音スレッドから受け取った音声チャンクを、CTranslate2専用workerへ渡すクラスです。
 *
 * <p>モデルはworkerスレッド開始時に一度だけ読み込み、録音中は{@link #submit(short[], int)}
 * で渡された PCM を一定時間ごとにまとめて推論します。推論結果は
 * {@link WhisperTranscriptionEvent} として {@link SystemEventHub} へ publish します。</p>
 */
public class WhisperTranscriptionWorker implements Runnable {
    /** ログ出力用タグです。 */
    private static final String TAG = WhisperTranscriptionWorker.class.getSimpleName();

    /** Whisper.cpp が想定する標準サンプリングレートです。 */
    public static final int DEFAULT_SAMPLE_RATE = 16000;

    /** 録音スレッドから受け取る音声チャンクの最大待機数です。 */
    private static final int QUEUE_CAPACITY = 128;

    /** Whisper モデルファイルの実ファイルパスです。 */
    private final String modelPath;

    /** 録音開始ごとに作られる session ID です。 */
    private final String sessionId;

    /** この worker の停止イベントを識別する ID です。 */
    private final String stopEventId;

    /** Whisper 推論設定です。 */
    private final WhisperSettings settings;

    /** 入力 PCM のサンプリングレートです。 */
    private final int sampleRate;

    /** 1 回の推論窓に必要なサンプル数です。 */
    private final int windowSamples;

    /** 次の推論窓へ残す重なり部分のサンプル数です。 */
    private final int overlapSamples;

    /** 停止時の最終推論に必要な最小サンプル数です。 */
    private final int minFinalSamples;
    /** 録音スレッドから渡された PCM チャンクを保存しておくバッファのプールです。 */
    private final ObjectPool<ShortAudioBuffer> audioBufferPool = new ObjectPool<>(
            ShortAudioBuffer::new,
            null,
            ShortAudioBuffer::clear,
            (buffer)->{Log.d(TAG,"deleted");},
            QUEUE_CAPACITY,
            QUEUE_CAPACITY);

    /** 録音スレッドから渡された PCM チャンクを受け取るキューです。 */
    private final ArrayBlockingQueue<ShortAudioBuffer> audioQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /** 推論窓に達するまで PCM をためるバッファです。 */
    private final ShortAudioBuffer pendingAudio;
    /** workerスレッドだけが使う再利用推論窓です。 */
    private final short[] inferenceWindowBuffer;

    /** worker スレッドの継続フラグです。 */
    private volatile boolean running;

    /** 新しい録音チャンクをキューへ受け付ける場合 true です。 */
    private volatile boolean acceptingAudio;

    /** キューを処理し終えた時点で停止する要求がある場合 true です。 */
    private volatile boolean drainStopRequested;

    /** 終了処理へ入っており、停止要求を取り消せない場合 true です。 */
    private volatile boolean terminating;

    /** 停止要求時に残り音声を最終推論してから終了する場合 true です。 */
    private volatile boolean finishAfterQueuedAudio;

    /** Whisper 推論を実行している Java スレッドです。 */
    private Thread workerThread;

    /** 同じ録音 session 内で発行する推論結果番号です。 */
    private int sequence;

    /** 推論済みとして破棄したサンプル数です。録音開始からの時刻計算に使います。 */
    private long processedSamples;

    /**
     * 既定のサンプリングレートと設定で Whisper 推論 worker を作成します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param sessionId 録音 session ID
     */
    public WhisperTranscriptionWorker(final String modelPath, final String sessionId) {
        this(modelPath, null, sessionId, WhisperSettings.defaultSettings());
    }

    /**
     * 既定のサンプリングレートで Whisper 推論 worker を作成します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param sessionId 録音 session ID
     * @param settings Whisper 推論設定
     */
    public WhisperTranscriptionWorker(
            final String modelPath,
            final String sessionId,
            final WhisperSettings settings
    ) {
        this(modelPath, null, sessionId, DEFAULT_SAMPLE_RATE, settings);
    }

    /**
     * 既定のサンプリングレートでVAD付きWhisper推論workerを作成します。
     *
     * @param modelPath Whisperモデルの実ファイルパス。例: {@code "/data/.../ggml-base.bin"}
     * @param vadModelPath VADモデルの実ファイルパス。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param sessionId 録音session ID。例: {@code "recording-1"}
     * @param settings Whisper推論設定。例: {@code WhisperSettings.defaultSettings()}
     */
    public WhisperTranscriptionWorker(
            final String modelPath,
            final String vadModelPath,
            final String sessionId,
            final WhisperSettings settings
    ) {
        this(modelPath, vadModelPath, sessionId, DEFAULT_SAMPLE_RATE, settings);
    }

    /**
     * Whisper 推論 worker を作成します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param sessionId 録音 session ID
     * @param sampleRate 入力 PCM のサンプリングレート
     * @param settings Whisper 推論設定
     */
    public WhisperTranscriptionWorker(
            final String modelPath,
            final String sessionId,
            final int sampleRate,
            final WhisperSettings settings
    ) {
        this(modelPath, null, sessionId, sampleRate, settings);
    }

    /**
     * サンプリングレートを指定してVAD付きWhisper推論workerを作成します。
     *
     * @param modelPath Whisperモデルの実ファイルパス。例: {@code "/data/.../ggml-base.bin"}
     * @param vadModelPath VADモデルの実ファイルパス。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param sessionId 録音session ID。例: {@code "recording-1"}
     * @param sampleRate 入力PCMのHz。例: {@code 16000}
     * @param settings Whisper推論設定。例: {@code WhisperSettings.defaultSettings()}
     */
    public WhisperTranscriptionWorker(
            final String modelPath,
            final String vadModelPath,
            final String sessionId,
            final int sampleRate,
            final WhisperSettings settings
    ) {
        this.modelPath = modelPath;
        this.sessionId = sessionId;
        this.stopEventId = StringBufferBuilderPool.Join("", sessionId, ":whisper");
        this.settings = settings == null ? WhisperSettings.defaultSettings() : settings;
        this.sampleRate = sampleRate;
        this.windowSamples = Math.max(1, sampleRate * this.settings.windowMs() / 1000);
        this.overlapSamples = Math.max(0, sampleRate * this.settings.overlapMs() / 1000);
        this.minFinalSamples = Math.max(1, sampleRate * this.settings.minFinalMs() / 1000);
        this.pendingAudio = new ShortAudioBuffer(windowSamples * 2);
        this.inferenceWindowBuffer = new short[windowSamples];
    }

    /** Whisper 推論スレッドを開始します。 */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        acceptingAudio = true;
        drainStopRequested = false;
        terminating = false;
        workerThread = new Thread(this, "WhisperTranscriptionWorker");
        workerThread.start();
    }

    /** この worker スレッドの停止イベントを待つための ID を返します。 */
    public String getStopEventId() {
        return stopEventId;
    }

    /** worker スレッドがまだ生きている場合 true を返します。 */
    public boolean isAlive() {
        final Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * 録音された音声チャンクを worker に渡します。
     *
     * @param samples 16kHz・モノラル・PCM16。例: {@code new short[8000]}
     * @param length samples のうち有効な要素数
     * @return キューへ追加できた場合true。例: {@code true}
     */
    public boolean submit(final short[] samples, final int length) {
        if (!running || !acceptingAudio || samples == null || samples.length == 0 || length <= 0) {
            return false;
        }
        final ShortAudioBuffer copy;
        synchronized (audioBufferPool) {
            copy = audioBufferPool.getOrCreate();
        }
        copy.append(samples, Math.min(length, samples.length));
        
        if (!audioQueue.offer(copy)) {
            final ShortAudioBuffer old = audioQueue.poll();
            if(old != null) {
                releaseAudioBuffer(old);
                Log.w(TAG, "Whisper queue is full. Dropped old audio chunk.");
            }
            if(!audioQueue.offer(copy)) {
                releaseAudioBuffer(copy);
                Log.w(TAG, "Whisper queue is full. Dropped current audio chunk.");
                return false;
            }

        }
        return true;
    }

    /**
     * worker に停止を要求します。
     *
     * <p>Thread.join() では待たず、{@link ThreadStoppedEvent} を Awaiter で待ってください。</p>
     */
    public synchronized boolean requestStop() {
        acceptingAudio = false;
        drainStopRequested = true;
        finishAfterQueuedAudio = true;

        return workerThread != null;
    }

    /**
     * キュー排出後の停止を取り消し、新しい音声の受付を再開します。
     *
     * @return 同じworkerで再開できた場合true。例: {@code true}
     */
    public synchronized boolean resumeAudioSubmission() {
        if (!running || workerThread == null || terminating) {
            return false;
        }
        drainStopRequested = false;
        finishAfterQueuedAudio = false;
        acceptingAudio = true;
        return true;
    }

    /**
     * 新しい音声を受け付けているか返します。
     *
     * @return 受付中ならtrue。例: {@code false}
     */
    public boolean isAcceptingAudio() {
        return running && acceptingAudio;
    }

    /**
     * Whisper worker スレッド本体です。
     *
     * <p>モデルを開き、録音キューを消費し、終了時に必ず停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();

        try(CTranslate2TranscriptionWorker cTranslate2Worker = new CTranslate2TranscriptionWorker(modelPath, settings)) {
            while (running && !currentThread.isInterrupted()) {
                //　キューに溜まったデータを取得
                final ShortAudioBuffer chunk = audioQueue.poll(200, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    // 窓に追加
                    pendingAudio.append(chunk);
                    releaseAudioBuffer(chunk);
                    // 一定以上ある場合は推論
                    while (pendingAudio.size() >= windowSamples && !currentThread.isInterrupted()) {
                        transcribeNextWindow(cTranslate2Worker,false);
                    }
                }
                if (beginTerminationIfDrained()) {
                    break;
                }
            }
            transcribeRemainingAudio(cTranslate2Worker,currentThread);
        } catch (InterruptedException e) {
            currentThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Whisper worker error", e);
            outputError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            // バッファクリア
            pendingAudio.clear();
            running = false;
            acceptingAudio = false;
            drainStopRequested = false;
            terminating = true;
            workerThread = null;
            publishStoppedEvent(currentThread, "");
        }
    }

    /**
     * 停止時にバッファへ残っている音声を最終結果として推論します。
     * @param currentThread worker 自身のスレッド
     */
    private void transcribeRemainingAudio(@NonNull CTranslate2TranscriptionWorker cTranslate2Worker, @NonNull final Thread currentThread) {
        drainQueuedAudio();
        final boolean hasRequiredAudio = pendingAudio.size() >= minFinalSamples;
        final boolean hasForcedFinalAudio = finishAfterQueuedAudio && pendingAudio.size() > 0;
        if (!currentThread.isInterrupted() && (hasRequiredAudio || hasForcedFinalAudio)) {
            transcribeNextWindow(cTranslate2Worker,true);
        }
    }

    /**
     * 停止要求前にキューへ入っていた音声を pendingAudio に移します。
     */
    private void drainQueuedAudio() {
        ShortAudioBuffer chunk;
        while ((chunk = audioQueue.poll()) != null) {
            pendingAudio.append(chunk);
            releaseAudioBuffer(chunk);
        }
    }

    /**
     * 複数スレッドから安全に音声バッファをプールへ返します。
     * @param buffer 返却対象。例: {@code chunk}
     */
    private void releaseAudioBuffer(@NonNull final ShortAudioBuffer buffer) {
        synchronized (audioBufferPool) {
            audioBufferPool.releaseOrDelete(buffer);
        }
    }

    /**
     * 停止要求後に入力キューが空なら終了状態へ遷移します。
     *
     * @return 終了処理へ進む場合true。例: {@code true}
     */
    private synchronized boolean beginTerminationIfDrained() {
        if (!drainStopRequested || !audioQueue.isEmpty()) {
            return false;
        }
        acceptingAudio = false;
        terminating = true;
        return true;
    }

    /**
     * バッファから 1 窓分の音声を取り出して Whisper 推論を行います。
     *
     * @param finalResult 停止時の最終推論なら true
     */
    private void transcribeNextWindow(@NonNull CTranslate2TranscriptionWorker cTranslate2Worker, final boolean finalResult) {
        final int sampleCount = finalResult
                ? pendingAudio.size()
                : windowSamples;
        pendingAudio.writeFirst(sampleCount, inferenceWindowBuffer);
        final long startMs = samplesToMs(processedSamples);
        final long durationMs = samplesToMs(sampleCount);

        final long startedAt = System.nanoTime();
        Log.d(TAG,"start transcription");
        final TranscriptionWorkerResult result =
                cTranslate2Worker.transcribe(inferenceWindowBuffer, sampleCount);
        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Log.d(TAG, result.text);
        Log.d(TAG, String.valueOf(result.speakerChanged));
        Log.d(TAG, StringBufferBuilderPool.Join("", "Whisper inference: ", processingTimeMs, "ms"));

        output(
                result.text,
                result.speakerChanged,
                finalResult,
                null,
                startMs,
                durationMs,
                processingTimeMs,
                finalResult ? WhisperTranscriptionTag.Stopping : WhisperTranscriptionTag.Recording
        );

        final int discardSamples = finalResult
                ? sampleCount
                : Math.max(1, sampleCount - overlapSamples);
        pendingAudio.discardFirst(discardSamples);
        processedSamples += discardSamples;
    }


    /**
     * Whisper の推論結果をイベントとして送信します。
     *
     * @param text 文字起こし本文
     * @param speakerChanged 話者が変わった可能性がある場合 true
     * @param finalResult 最終結果なら true
     * @param errorMessage エラーメッセージ
     * @param startMs 録音開始からの開始時刻。ミリ秒
     * @param durationMs 対象音声の長さ。ミリ秒
     * @param processingTimeMs Whisper 推論 1 回にかかった処理時間。ミリ秒
     * @param tag 文字起こしの発行元タグ。例: {@code WhisperTranscriptionTag.Recording}
     */
    private void output(
            final String text,
            final boolean speakerChanged,
            final boolean finalResult,
            final String errorMessage,
            final long startMs,
            final long durationMs,
            final long processingTimeMs,
            final WhisperTranscriptionTag tag
    ) {
        SystemEventHub.publish(new WhisperTranscriptionEvent(
                sessionId,
                sequence++,
                text == null ? "" : text,
                speakerChanged,
                finalResult,
                errorMessage,
                startMs,
                durationMs,
                processingTimeMs,
                settings.model().key(),
                tag
        ));
    }

    private void outputError(final String message) {
        output("", false, false, message, samplesToMs(processedSamples), 0, 0,
                WhisperTranscriptionTag.Stopping);
    }

    /**
     * Whisper worker が停止したことをイベントとして通知します。
     *
     * @param thread 停止したスレッド
     * @param errorMessage エラー終了した場合のメッセージ
     */
    private void publishStoppedEvent(@NonNull final Thread thread, final String errorMessage) {
        SystemEventHub.publish(new ThreadStoppedEvent(
                stopEventId,
                "Whisper",
                thread.getName(),
                thread.isInterrupted(),
                errorMessage
        ));
    }

    /** サンプル数を録音開始からのミリ秒へ変換します。 */
    private long samplesToMs(final long samples) {
        return samples * 1000L / sampleRate;
    }

    /**
     * 静的ネストクラス
     */
    private static final class ShortAudioBuffer implements AutoCloseable {
        private static final int DEFAULTCAPACITY = 1024;
        /** 実データを保持する内部配列です。 */
        private short[] buffer;
        /** 現在バッファに入っている有効サンプル数です。 */
        private int size;

        ShortAudioBuffer(){this(DEFAULTCAPACITY);}
        ShortAudioBuffer(int initialCapacity) {
            buffer = new short[Math.max(1, initialCapacity)];
        }

        /** バッファを返します。 */
        short[] buffer() {
            return buffer;
        }
        /** バッファ内の有効サンプル数を返します。 */
        int size() {
            return size;
        }

        /** PCM サンプルを指定の長さ分末尾へ追加します。 */
        int append(@NonNull final short[] samples,final int length) {
            ensureCapacity(size + length);
            System.arraycopy(samples, 0, buffer, size, length);
            size += length;
            return length;
        }
        /** PCM サンプルを末尾へ追加します。 */
        int append(@NonNull final short[] samples) {
            return append(samples,samples.length);
        }

        /**
         * 自身に指定のShortAudioBufferをコピーして追加します。
         * @param other コピー元のバッファ
         */
        int append(@NonNull final ShortAudioBuffer other) {
            return append(other.buffer,other.size);
        }


        /** 先頭から指定サンプル数を,対象バッファの先頭からに書き込みます。 */
        int writeFirst(final int count, @NonNull final short[] writeBuffer){
            final int minSize = Math.min(writeBuffer.length, size);
            final int copyLength = Math.min(count, minSize);
            System.arraycopy(buffer, 0, writeBuffer, 0, copyLength);
            return copyLength;
        }

        /** 先頭から指定サンプル数を破棄します。 */
        void discardFirst(final int count) {
            if (count <= 0) {
                return;
            }

            if (count >= size) {
                size = 0;
                return;
            }

            int remaining = size - count;
            // バッファのcount~endを0からremainingまで移動
            System.arraycopy(buffer, count, buffer, 0, remaining);
            size = remaining;
        }

        /** 指定容量を格納できるように内部配列を拡張します。 */
        private void ensureCapacity(int capacity) {
            if (capacity <= buffer.length) {
                return;
            }

            int newCapacity = buffer.length;
            while (newCapacity < capacity) {
                newCapacity *= 2;
            }
            buffer = Arrays.copyOf(buffer, newCapacity);
        }
        private void trimToSize(){
            buffer = Arrays.copyOf(buffer,size);
        }
        void clear(){
            size = 0;
        }
        @Override
        public void close(){
            clear();
        }
    }

}
