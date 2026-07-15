package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import Utils.Pool.ObjectPool;
import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;
import Utils.StringPool.StringBufferBuilderPool;
import Whisper.WhisperBridge;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/**
 * 録音スレッドから受け取った音声チャンクを、別スレッドで Whisper.cpp に渡すクラスです。
 *
 * <p>モデルは worker スレッド開始時に一度だけ読み込み、録音中は {@link #submit(float[], int)}
 * で渡された PCM を一定時間ごとにまとめて推論します。推論結果は
 * {@link WhisperTranscriptionEvent} として {@link SystemEventHub} へ publish します。</p>
 */
public class WhisperTranscriptionWorker implements Runnable {
    /** ログ出力用タグです。 */
    private static final String TAG = WhisperTranscriptionWorker.class.getSimpleName();

    /** Whisper.cpp が想定する標準サンプリングレートです。 */
    public static final int DEFAULT_SAMPLE_RATE = 16000;

    /** 録音スレッドから受け取る音声チャンクの最大待機数です。 */
    private static final int QUEUE_CAPACITY = 32;

    /** Whisper モデルファイルの実ファイルパスです。 */
    private final String modelPath;

    /** whisper.cpp内蔵VADが読み込むモデルファイルの実ファイルパスです。 */
    private final String vadModelPath;

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
    private final ObjectPool<FloatAudioBuffer> audioBufferPool = new ObjectPool<>(
            FloatAudioBuffer::new,
            null,
            FloatAudioBuffer::clear,
            (buffer)->{Log.d(TAG,"deleted");},
            QUEUE_CAPACITY,
            QUEUE_CAPACITY);

    /** 録音スレッドから渡された PCM チャンクを受け取るキューです。 */
    private final ArrayBlockingQueue<FloatAudioBuffer> audioQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /** 推論窓に達するまで PCM をためるバッファです。 */
    private final FloatAudioBuffer pendingAudio;

    /** worker スレッドの継続フラグです。 */
    private volatile boolean running;

    /** 停止要求時に残り音声を最終推論してから終了する場合 true です。 */
    private volatile boolean finishAfterQueuedAudio;

    /** Whisper 推論を実行している Java スレッドです。 */
    private Thread workerThread;

    /** native 側の Whisper context ハンドルです。 */
    private long context;

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
        this.vadModelPath = vadModelPath;
        this.sessionId = sessionId;
        this.stopEventId = StringBufferBuilderPool.Join("", sessionId, ":whisper");
        this.settings = settings == null ? WhisperSettings.defaultSettings() : settings;
        this.sampleRate = sampleRate;
        this.windowSamples = Math.max(1, sampleRate * this.settings.windowMs() / 1000);
        this.overlapSamples = Math.max(0, sampleRate * this.settings.overlapMs() / 1000);
        this.minFinalSamples = Math.max(1, sampleRate * this.settings.minFinalMs() / 1000);
        this.pendingAudio = new FloatAudioBuffer(windowSamples * 2);
    }

    /** Whisper 推論スレッドを開始します。 */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
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
     * @param samples 16kHz・モノラル・float PCM
     * @param length samples のうち有効な要素数
     */
    public void submit(final float[] samples, final int length) {
        if (!running || samples == null || length <= 0) {
            return;
        }
        final FloatAudioBuffer copy = audioBufferPool.getOrCreate();
        copy.append(samples);
        
        if (!audioQueue.offer(copy)) {
            final FloatAudioBuffer old = audioQueue.poll();
            if(old != null) {
                audioBufferPool.releaseOrDelete(old);
                Log.w(TAG, "Whisper queue is full. Dropped old audio chunk.");
            }
            if(!audioQueue.offer(copy)) {
                audioBufferPool.releaseOrDelete(copy);
                Log.w(TAG, "Whisper queue is full. Dropped current audio chunk.");
            }

        }
    }

    /**
     * worker に停止を要求します。
     *
     * <p>Thread.join() では待たず、{@link ThreadStoppedEvent} を Awaiter で待ってください。</p>
     */
    public synchronized boolean requestStop() {
        running = false;
        finishAfterQueuedAudio = true;

        if (workerThread == null) {
            return false;
        }

        return true;
    }

    /**
     * Whisper worker スレッド本体です。
     *
     * <p>モデルを開き、録音キューを消費し、終了時に必ず停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();

        try {
            context = openContext(settings);
            if (context == 0) {
                outputError(StringBufferBuilderPool.Join("", "model load failed: ", modelPath));
                return;
            }

            while (running && !currentThread.isInterrupted()) {
                //　キューに溜まったデータを取得
                final FloatAudioBuffer chunk = audioQueue.poll(200, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    // 窓に追加
                    pendingAudio.append(chunk);
                    // 一定以上ある場合は推論
                    while (pendingAudio.size() >= windowSamples && !currentThread.isInterrupted()) {
                        transcribeNextWindow(false);
                    }
                }
            }
            transcribeRemainingAudio(currentThread);
        } catch (InterruptedException e) {
            currentThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Whisper worker error", e);
            outputError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (context != 0) {
                WhisperBridge.freeContext(context);
                context = 0;
            }
            // バッファクリア
            pendingAudio.clear();
            running = false;
            workerThread = null;
            publishStoppedEvent(currentThread, "");
        }
    }

    /**
     * 停止時にバッファへ残っている音声を最終結果として推論します。
     * @param currentThread worker 自身のスレッド
     */
    private void transcribeRemainingAudio(@NonNull final Thread currentThread) {
        drainQueuedAudio();
        final boolean hasRequiredAudio = pendingAudio.size() >= minFinalSamples;
        final boolean hasForcedFinalAudio = finishAfterQueuedAudio && pendingAudio.size() > 0;
        if (!currentThread.isInterrupted() && (hasRequiredAudio || hasForcedFinalAudio)) {
            transcribeNextWindow(true);
        }
    }

    /**
     * 停止要求前にキューへ入っていた音声を pendingAudio に移します。
     */
    private void drainQueuedAudio() {
        FloatAudioBuffer chunk;
        while ((chunk = audioQueue.poll()) != null) {
            pendingAudio.append(chunk);
        }
    }

    /** native の Whisper context を作成します。 */
    private long openContext(@NonNull final WhisperSettings settings) {
        final WhisperBridge.ContextParams params = WhisperBridge.defaultContextParams();
        params.useGpu = settings.useGpu();
        return WhisperBridge.initFromFile(modelPath, params);
    }

    /**
     * バッファから 1 窓分の音声を取り出して Whisper 推論を行います。
     *
     * @param finalResult 停止時の最終推論なら true
     */
    private void transcribeNextWindow(boolean finalResult) {
        final int sampleCount = finalResult
                ? pendingAudio.size()
                : windowSamples;
        final float[] samples = pendingAudio.copyFirst(sampleCount);
        final long startMs = samplesToMs(processedSamples);
        final long durationMs = samplesToMs(samples.length);

        final long startedAt = System.nanoTime();
        Log.d(TAG,"start transcription");
        final TranscriptionResult result = transcribe(samples);
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
     * 指定された PCM を Whisper.cpp へ渡し、文字起こし本文と話者変化フラグを取得します。
     *
     * @param samples 16kHz・モノラル・float PCM
     * @return 文字起こし結果
     */
    @NonNull
    private TranscriptionResult transcribe(@NonNull final float[] samples) {
        final WhisperBridge.FullParams params =
                WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY);
        params.printProgress = false;
        params.printSpecial = false;
        params.printRealtime = false;
        params.printTimestamps = settings.printTimestamps();
        params.noContext = settings.noContext();
        params.language = settings.language();
        params.nThreads = Math.min(
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        if (vadModelPath != null && !vadModelPath.trim().isEmpty()) {
            WhisperVadConfig.enable(params, vadModelPath);
        }

        final int result = WhisperBridge.full(context, params, samples);
        if (result != 0) {
            return TranscriptionResult.defaultValue;
        }

        return collectTranscriptionResult();
    }
    /**
     * 指定された PCM を Whisper.cpp へ渡し、文字起こし本文と話者変化フラグを取得します。
     *
     * @param samples 16kHz・モノラル・float PCM
     * @return 文字起こし結果
     */
    @NonNull
    private TranscriptionResult transcribeParallel(@NonNull final float[] samples) {
        final WhisperBridge.FullParams params =
                WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY);
        params.printProgress = false;
        params.printSpecial = false;
        params.printRealtime = false;
        params.printTimestamps = settings.printTimestamps();
        params.noContext = settings.noContext();
        params.language = settings.language();
        params.nThreads = Math.min(
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        if (vadModelPath != null && !vadModelPath.trim().isEmpty()) {
            WhisperVadConfig.enable(params, vadModelPath);
        }

        final int result = WhisperBridge.fullParallel(context, params, samples,params.nThreads);
        if (result != 0) {
            return TranscriptionResult.defaultValue;
        }

        return collectTranscriptionResult();
    }

    /**
     * Whisper.cpp の segment API から本文と話者変化フラグを回収します。
     *
     * @return 文字起こし結果
     */
    @NonNull
    @Contract(" -> new")
    private TranscriptionResult collectTranscriptionResult() {
        final int segmentCount = WhisperBridge.fullNSegments(context);
        try(PooledStringBuilder sb = ScopableUtility.getBuilder()){
            boolean speakerChanged = false;
            for (int i = 0; i < segmentCount; i++) {
                sb.append(WhisperBridge.fullSegmentText(context, i));
                speakerChanged |= WhisperBridge.fullSegmentSpeakerTurnNext(context, i);
            }
            return new TranscriptionResult(
                    sb.toString(),
                    speakerChanged
            );
        }
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
    private static final class FloatAudioBuffer implements AutoCloseable {
        private static final int DEFAULTCAPACITY = 1024;
        /** 実データを保持する内部配列です。 */
        private float[] buffer;
        /** 現在バッファに入っている有効サンプル数です。 */
        private int size;

        FloatAudioBuffer(){this(DEFAULTCAPACITY);}
        FloatAudioBuffer(int initialCapacity) {
            buffer = new float[Math.max(1, initialCapacity)];
        }

        /** バッファを返します。 */
        float[] buffer() {
            return buffer;
        }
        /** バッファ内の有効サンプル数を返します。 */
        int size() {
            return size;
        }

        /** PCM サンプルを指定の長さ分末尾へ追加します。 */
        int append(@NonNull final float[] samples,final int length) {
            ensureCapacity(size + length);
            System.arraycopy(samples, 0, buffer, size, length);
            size += length;
            return length;
        }
        /** PCM サンプルを末尾へ追加します。 */
        int append(@NonNull final float[] samples) {
            return append(samples,samples.length);
        }

        /**
         * 自身に を指定の FloatAudioBuffer をコピーして追加します。
         * @param other コピー元のバッファ
         */
        int append(@NonNull final FloatAudioBuffer other) {
            return append(other.buffer,other.size);
        }


        /** 先頭から指定サンプル数をコピーします。 */
        @NonNull
        float[] copyFirst(final int count) {
            int copyLength = Math.min(count, size);
            return Arrays.copyOf(buffer, copyLength);
        }
        /** 先頭から指定サンプル数を,対象バッファの先頭からに書き込みます。 */
        int writeFirst(final int count, @NonNull final float[] writeBuffer){
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

    /** 1 回の Whisper 推論から取り出した結果です。 */
    private static class TranscriptionResult {
        /** 文字起こし本文です。 */
        final String text;

        /** この結果の直後に話者が変わった可能性がある場合 true です。 */
        final boolean speakerChanged;
        /**
         * デフォルト値です。
         */
        public static TranscriptionResult defaultValue = new TranscriptionResult("",false);
        TranscriptionResult(String text, boolean speakerChanged) {
            this.text = text == null ? "" : text;
            this.speakerChanged = speakerChanged;
        }
    }
}
