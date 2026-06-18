package jp.ac.gifu_u.programmingjissen2;

import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import Whisper.WhisperBridge;
import events.SystemEventHub;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperTranscriptionEvent;

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
    private static final int QUEUE_CAPACITY = 12;

    /** 1 回の Whisper 推論に使う音声窓の長さです。ミリ秒。 */
    private static final int WINDOW_MS = 5000;

    /** 連続する推論窓の重なり部分です。ミリ秒。 */
    private static final int OVERLAP_MS = 1000;

    /** 停止時に最終推論として処理する最小音声長です。ミリ秒。 */
    private static final int MIN_FINAL_MS = 1000;

    /** Whisper モデルファイルの実ファイルパスです。 */
    private final String modelPath;

    /** 録音開始ごとに作られる session ID です。 */
    private final String sessionId;

    /** この worker の停止イベントを識別する ID です。 */
    private final String stopEventId;

    /** 入力 PCM のサンプリングレートです。 */
    private final int sampleRate;

    /** 1 回の推論窓に必要なサンプル数です。 */
    private final int windowSamples;

    /** 次の推論窓へ残す重なり部分のサンプル数です。 */
    private final int overlapSamples;

    /** 停止時の最終推論に必要な最小サンプル数です。 */
    private final int minFinalSamples;

    /** 録音スレッドから渡された PCM チャンクを受け取るキューです。 */
    private final ArrayBlockingQueue<float[]> audioQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    /** 推論窓に達するまで PCM をためるバッファです。 */
    private final FloatAudioBuffer pendingAudio;

    /** worker スレッドの継続フラグです。 */
    private volatile boolean running;

    /** Whisper 推論を実行している Java スレッドです。 */
    private Thread workerThread;

    /** native 側の Whisper context ハンドルです。 */
    private long context;

    /** 同じ録音 session 内で発行する推論結果番号です。 */
    private int sequence;

    /** 推論済みとして破棄したサンプル数です。録音開始からの時刻計算に使います。 */
    private long processedSamples;

    /**
     * 既定のサンプリングレート、推論窓、重なり幅で Whisper 推論 worker を作成します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param sessionId 録音 session ID
     */
    public WhisperTranscriptionWorker(String modelPath, String sessionId) {
        this(modelPath, sessionId, DEFAULT_SAMPLE_RATE, WINDOW_MS, OVERLAP_MS);
    }

    /**
     * Whisper 推論 worker を作成します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param sessionId 録音 session ID
     * @param sampleRate 入力 PCM のサンプリングレート
     * @param windowMs 1 回の推論に使う音声長。ミリ秒
     * @param overlapMs 推論窓同士で重ねる音声長。ミリ秒
     */
    public WhisperTranscriptionWorker(
            String modelPath,
            String sessionId,
            int sampleRate,
            int windowMs,
            int overlapMs
    ) {
        this.modelPath = modelPath;
        this.sessionId = sessionId;
        this.stopEventId = sessionId + ":whisper";
        this.sampleRate = sampleRate;
        this.windowSamples = Math.max(1, sampleRate * windowMs / 1000);
        this.overlapSamples = Math.max(0, sampleRate * overlapMs / 1000);
        this.minFinalSamples = Math.max(1, sampleRate * MIN_FINAL_MS / 1000);
        this.pendingAudio = new FloatAudioBuffer(windowSamples * 2);
    }

    /**
     * Whisper 推論スレッドを開始します。
     */
    public synchronized void start() {
        if (running) {
            return;
        }

        running = true;
        workerThread = new Thread(this, "WhisperTranscriptionWorker");
        workerThread.start();
    }

    /**
     * この worker スレッドの停止イベントを待つための ID を返します。
     */
    public String getStopEventId() {
        return stopEventId;
    }

    /**
     * worker スレッドがまだ生きている場合 true を返します。
     */
    public boolean isAlive() {
        Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * 録音された音声チャンクを worker に渡します。
     *
     * @param samples 16kHz・モノラル・float PCM
     * @param length samples のうち有効な要素数
     */
    public void submit(float[] samples, int length) {
        if (!running || samples == null || length <= 0) {
            return;
        }

        float[] copy = Arrays.copyOf(samples, Math.min(length, samples.length));
        if (!audioQueue.offer(copy)) {
            audioQueue.poll();
            audioQueue.offer(copy);
            Log.w(TAG, "Whisper queue is full. Dropped old audio chunk.");
        }
    }

    /**
     * worker に停止を要求します。
     *
     * <p>Thread.join() では待たず、{@link ThreadStoppedEvent} を Awaiter で待ってください。</p>
     */
    public synchronized boolean requestStop() {
        running = false;

        if (workerThread == null) {
            return false;
        }

        workerThread.interrupt();
        return true;
    }

    /**
     * Whisper worker スレッド本体です。
     *
     * <p>モデルを開き、録音キューを消費し、終了時に必ず停止イベントを発行します。</p>
     */
    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        String stopErrorMessage = null;

        try {
            stopErrorMessage = runWorkerLoop(currentThread);
        } catch (InterruptedException e) {
            currentThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Whisper worker error", e);
            stopErrorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            outputError(stopErrorMessage);
        } finally {
            releaseContext();
            running = false;
            workerThread = null;
            publishStoppedEvent(currentThread, stopErrorMessage);
        }
    }

    /**
     * Whisper context を開き、キュー内の音声を推論し、停止時の残り音声を処理します。
     *
     * @param currentThread worker 自身のスレッド
     * @return エラー終了した場合のメッセージ。正常終了なら null
     * @throws InterruptedException キュー待機中に interrupt された場合
     */
    private String runWorkerLoop(Thread currentThread) throws InterruptedException {
        String openError = openWhisperContext();
        if (openError != null) {
            outputError(openError);
            return openError;
        }

        consumeQueuedAudio(currentThread);
        transcribeRemainingAudio(currentThread);
        return null;
    }

    /**
     * Whisper context を開きます。
     *
     * @return エラーメッセージ。成功時は null
     */
    private String openWhisperContext() {
        context = openContext();
        if (context == 0) {
            return "model load failed: " + modelPath;
        }
        return null;
    }

    /**
     * 録音 worker から届く PCM キューを消費し続けます。
     *
     * @param currentThread worker 自身のスレッド
     * @throws InterruptedException キュー待機中に interrupt された場合
     */
    private void consumeQueuedAudio(Thread currentThread) throws InterruptedException {
        while (shouldContinue(currentThread)) {
            float[] chunk = pollAudio();
            if (chunk != null) {
                processAudioChunk(chunk, currentThread);
            }
        }
    }

    /**
     * 1 チャンク分の PCM を推論待ちバッファへ追加し、推論窓に達したら処理します。
     *
     * @param chunk 録音 worker から届いた PCM チャンク
     * @param currentThread worker 自身のスレッド
     */
    private void processAudioChunk(float[] chunk, Thread currentThread) {
        pendingAudio.append(chunk);
        while (pendingAudio.size() >= windowSamples && !currentThread.isInterrupted()) {
            transcribeNextWindow(false);
        }
    }

    /**
     * 停止時にバッファへ残っている音声を最終結果として推論します。
     *
     * @param currentThread worker 自身のスレッド
     */
    private void transcribeRemainingAudio(Thread currentThread) {
        if (!currentThread.isInterrupted() && pendingAudio.size() >= minFinalSamples) {
            transcribeNextWindow(true);
        }
    }

    /**
     * worker ループを継続できるか判定します。
     *
     * @param currentThread worker 自身のスレッド
     * @return running かつ interrupt されていなければ true
     */
    private boolean shouldContinue(Thread currentThread) {
        return running && !currentThread.isInterrupted();
    }

    /**
     * native の Whisper context を作成します。
     *
     * @return native context ハンドル。失敗時は 0
     */
    private long openContext() {
        WhisperBridge.ContextParams params = WhisperBridge.defaultContextParams();
        return WhisperBridge.initFromFile(modelPath, params);
    }

    /**
     * 録音キューから PCM チャンクを取り出します。
     *
     * @return PCM チャンク。タイムアウト時は null
     * @throws InterruptedException 待機中に interrupt された場合
     */
    private float[] pollAudio() throws InterruptedException {
        return audioQueue.poll(200, TimeUnit.MILLISECONDS);
    }

    /**
     * バッファから 1 窓分の音声を取り出して Whisper 推論を行います。
     *
     * @param finalResult 停止時の最終推論なら true
     */
    private void transcribeNextWindow(boolean finalResult) {
        int sampleCount = finalResult
                ? pendingAudio.size()
                : windowSamples;
        float[] samples = pendingAudio.copyFirst(sampleCount);
        long startMs = samplesToMs(processedSamples);
        long durationMs = samplesToMs(samples.length);

        TranscriptionResult result = transcribe(samples);
        output(result.text, result.speakerChanged, finalResult, null, startMs, durationMs);

        int discardSamples = finalResult
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
    private TranscriptionResult transcribe(float[] samples) {
        WhisperBridge.FullParams params =
                WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY);
        params.printProgress = false;
        params.printSpecial = false;
        params.printRealtime = false;
        params.printTimestamps = false;
        params.noContext = true;
        params.language = "ja";
        params.nThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

        int result = WhisperBridge.full(context, params, samples);
        if (result != 0) {
            return new TranscriptionResult("", false);
        }

        return collectTranscriptionResult();
    }

    /**
     * Whisper.cpp の segment API から本文と話者変化フラグを回収します。
     *
     * @return 文字起こし結果
     */
    private TranscriptionResult collectTranscriptionResult() {
        StringBuilder builder = new StringBuilder();
        boolean speakerChanged = false;
        int segmentCount = WhisperBridge.fullNSegments(context);

        for (int i = 0; i < segmentCount; i++) {
            builder.append(WhisperBridge.fullSegmentText(context, i));
            speakerChanged |= WhisperBridge.fullSegmentSpeakerTurnNext(context, i);
        }

        return new TranscriptionResult(builder.toString().trim(), speakerChanged);
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
     */
    private void output(
            String text,
            boolean speakerChanged,
            boolean finalResult,
            String errorMessage,
            long startMs,
            long durationMs
    ) {
        SystemEventHub.publish(new WhisperTranscriptionEvent(
                sessionId,
                sequence++,
                text == null ? "" : text,
                speakerChanged,
                finalResult,
                errorMessage,
                startMs,
                durationMs
        ));
    }

    private void outputError(String message) {
        output("", false, false, message, samplesToMs(processedSamples), 0);
    }

    /**
     * Whisper worker が停止したことをイベントとして通知します。
     *
     * @param thread 停止したスレッド
     * @param errorMessage エラー終了した場合のメッセージ
     */
    private void publishStoppedEvent(Thread thread, String errorMessage) {
        SystemEventHub.publish(new ThreadStoppedEvent(
                stopEventId,
                "Whisper",
                thread.getName(),
                thread.isInterrupted(),
                errorMessage
        ));
    }

    /**
     * サンプル数を録音開始からのミリ秒へ変換します。
     *
     * @param samples サンプル数
     * @return ミリ秒
     */
    private long samplesToMs(long samples) {
        return samples * 1000L / sampleRate;
    }

    /**
     * native の Whisper context を解放します。
     */
    private void releaseContext() {
        if (context != 0) {
            WhisperBridge.freeContext(context);
            context = 0;
        }
    }

    private static class FloatAudioBuffer {
        /** 実データを保持する内部配列です。 */
        private float[] buffer;

        /** 現在バッファに入っている有効サンプル数です。 */
        private int size;

        FloatAudioBuffer(int initialCapacity) {
            buffer = new float[Math.max(1, initialCapacity)];
        }

        /**
         * バッファ内の有効サンプル数を返します。
         */
        int size() {
            return size;
        }

        /**
         * PCM サンプルを末尾へ追加します。
         */
        void append(float[] samples) {
            ensureCapacity(size + samples.length);
            System.arraycopy(samples, 0, buffer, size, samples.length);
            size += samples.length;
        }

        /**
         * 先頭から指定サンプル数をコピーします。
         */
        float[] copyFirst(int count) {
            int copyLength = Math.min(count, size);
            return Arrays.copyOf(buffer, copyLength);
        }

        /**
         * 先頭から指定サンプル数を破棄します。
         */
        void discardFirst(int count) {
            if (count <= 0) {
                return;
            }

            if (count >= size) {
                size = 0;
                return;
            }

            int remaining = size - count;
            System.arraycopy(buffer, count, buffer, 0, remaining);
            size = remaining;
        }

        /**
         * 指定容量を格納できるように内部配列を拡張します。
         */
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
    }

    /**
     * 1 回の Whisper 推論から取り出した結果です。
     */
    private static class TranscriptionResult {
        /** 文字起こし本文です。 */
        final String text;

        /** この結果の直後に話者が変わった可能性がある場合 true です。 */
        final boolean speakerChanged;

        /**
         * 文字起こし結果を作成します。
         */
        TranscriptionResult(String text, boolean speakerChanged) {
            this.text = text == null ? "" : text;
            this.speakerChanged = speakerChanged;
        }
    }
}
