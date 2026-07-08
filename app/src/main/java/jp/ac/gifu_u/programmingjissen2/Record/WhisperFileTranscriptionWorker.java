package jp.ac.gifu_u.programmingjissen2.Record;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import Utils.MyUtils;
import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;
import Utils.StringPool.StringBufferBuilderPool;
import Whisper.WhisperBridge;
import events.SystemEventHub;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.Record.Utility.AudioFilePcmDecoder;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** 既存音声ファイルを読み込み、Whisper.cpp で一括文字起こしする worker です。 */
public final class WhisperFileTranscriptionWorker implements Runnable {
    private static final String TAG = WhisperFileTranscriptionWorker.class.getSimpleName();

    private final Context context;
    private final Uri audioUri;
    private final String sessionId;
    private final WhisperSettings settings;
    private final CompletionListener completionListener;

    private Thread workerThread;

    /** ファイル文字起こし完了時に呼ばれる listener です。 */
    public interface CompletionListener {
        /**
         * ファイル文字起こしの終了を通知します。
         *
         * @param sessionId ファイル文字起こし session ID。例: {@code "file-abc"}
         * @param errorMessage エラー時のメッセージ。成功時は空文字。例: {@code "audio track not found"}
         */
        void onComplete(String sessionId, String errorMessage);
    }

    /**
     * ファイル文字起こし worker を作成します。
     *
     * @param context モデルと音声 URI を読む Context。例: {@code activity}
     * @param audioUri 文字起こし対象の音声 URI。例: {@code content://media/...}
     * @param sessionId 結果イベントに付与する ID。例: {@code "file-1a2b"}
     * @param settings Whisper 推論設定。例: {@code WhisperSettings.defaultSettings()}
     * @param completionListener 終了通知先。例: {@code this::onFileTranscriptionComplete}
     */
    public WhisperFileTranscriptionWorker(
            @NonNull final Context context,
            @NonNull final Uri audioUri,
            @NonNull final String sessionId,
            final WhisperSettings settings,
            final CompletionListener completionListener
    ) {
        this.context = context.getApplicationContext();
        this.audioUri = audioUri;
        this.sessionId = sessionId;
        this.settings = settings == null ? WhisperSettings.defaultSettings() : settings;
        this.completionListener = completionListener;
    }

    /**
     * ファイル文字起こしスレッドを開始します。
     *
     * @return 開始できた場合 true。例: {@code true}
     */
    public synchronized boolean start() {
        if (isAlive()) {
            return false;
        }

        workerThread = new Thread(this, "WhisperFileTranscriptionWorker");
        workerThread.start();
        return true;
    }

    /**
     * worker スレッドが生存しているかを返します。
     *
     * @return 生存中なら true。例: {@code false}
     */
    public boolean isAlive() {
        final Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /**
     * worker スレッド本体です。
     *
     * @throws RuntimeException run() 内では外へ送出せず、イベントと completionListener に変換します
     */
    @Override
    public void run() {
        String errorMessage = "";
        try {
            final String modelPath = MyUtils.prepareModelPath(context, settings.model().assetName());
            final DecodedAudio audio = AudioFilePcmDecoder.decodeToWhisperPcm(context, audioUri);
            transcribe(modelPath, audio);
        } catch (Exception e) {
            Log.e(TAG, "File transcription failed", e);
            errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            publishError(errorMessage);
        } finally {
            workerThread = null;
            if (completionListener != null) {
                completionListener.onComplete(sessionId, errorMessage);
            }
        }
    }

    private void transcribe(
            @NonNull final String modelPath,
            @NonNull final DecodedAudio audio
    ) throws IOException {
        final WhisperBridge.ContextParams contextParams = WhisperBridge.defaultContextParams();
        contextParams.useGpu = settings.useGpu();
        final long whisperContext = WhisperBridge.initFromFile(modelPath, contextParams);
        if (whisperContext == 0) {
            throw new IOException("model load failed: " + modelPath);
        }

        try {
            final long startedAt = System.nanoTime();
            final int result = WhisperBridge.full(whisperContext, createFullParams(),
                    audio.samples());
            final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startedAt);

            if (result != 0) {
                throw new IOException("Whisper inference failed: " + result);
            }

            publishResult(whisperContext, audio.durationMs(), processingTimeMs);
        } finally {
            WhisperBridge.freeContext(whisperContext);
        }
    }

    @NonNull
    private WhisperBridge.FullParams createFullParams() {
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
        return params;
    }

    private void publishResult(final long whisperContext, final long durationMs,
            final long processingTimeMs) {
        try (PooledStringBuilder sb = ScopableUtility.getBuilder()) {
            final int segmentCount = WhisperBridge.fullNSegments(whisperContext);
            boolean speakerChanged = false;
            for (int i = 0; i < segmentCount; i++) {
                sb.append(WhisperBridge.fullSegmentText(whisperContext, i));
                speakerChanged |= WhisperBridge.fullSegmentSpeakerTurnNext(whisperContext, i);
            }

            SystemEventHub.publish(new WhisperTranscriptionEvent(sessionId, 0, sb.toString(),
                    speakerChanged, true, null, 0, durationMs, processingTimeMs,
                    settings.model().key()));
        }
    }

    private void publishError(final String message) {
        SystemEventHub.publish(new WhisperTranscriptionEvent(
                sessionId,
                0,
                "",
                false,
                true,
                StringBufferBuilderPool.Join("", "ファイル文字起こしに失敗しました: ", message),
                0,
                0,
                0,
                settings.model().key()
        ));
    }
}
