package jp.ac.gifu_u.programmingjissen2.Record;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import Utils.MyUtils;
import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;
import Utils.StringPool.StringBufferBuilderPool;
import CTranslate2.CTranslate2Bridge;
import Whisper.WhisperBridge;
import events.SystemEventHub;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import jp.ac.gifu_u.programmingjissen2.Record.Utility.AudioFilePcmDecoder;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextFormatter;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextRepository;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperVadConfig;

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
            final String modelPath = settings.useCTranslate2()
                    ? MyUtils.prepareModelDirectory(
                            context,
                            settings.model().cTranslate2AssetDirectory())
                    : MyUtils.prepareModelPath(context, settings.model().assetName());
            final String vadModelPath = settings.useCTranslate2()
                    ? ""
                    : MyUtils.prepareModelPath(context, WhisperVadConfig.MODEL_ASSET_NAME);
            final DecodedAudio audio = AudioFilePcmDecoder.decodeToWhisperPcm(context, audioUri);
            if (settings.useCTranslate2()) {
                transcribeCTranslate2(modelPath, audio);
            } else {
                transcribe(modelPath, vadModelPath, audio);
            }
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

    /**
     * 読み込み済み音声を内蔵VADで発話区間へ絞ってから文字起こしします。
     *
     * @param modelPath Whisperモデルの実ファイルパス。例: {@code "/data/.../ggml-base.bin"}
     * @param vadModelPath VADモデルの実ファイルパス。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param audio 16kHz・モノラルへ変換済みの音声。例: {@code decodedAudio}
     * @throws IOException モデル初期化または推論に失敗した場合
     */
    private void transcribe(
            @NonNull final String modelPath,
            @NonNull final String vadModelPath,
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
            final int result = WhisperBridge.full(whisperContext, createFullParams(vadModelPath),
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

    /**
     * 読み込み済み音声をCTranslate2で一括文字起こしします。
     *
     * @param modelDirectory 変換済みモデルディレクトリ。例: {@code "/data/.../ctranslate2/base"}
     * @param audio 16kHz・モノラル音声。例: {@code decodedAudio}
     * @throws IllegalStateException モデル読み込みまたは推論に失敗した場合
     */
    private void transcribeCTranslate2(
            @NonNull final String modelDirectory,
            @NonNull final DecodedAudio audio
    ) {
        final int threads = Math.min(
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        final long startedAt = System.nanoTime();
        final String formatted;
        try (CTranslate2Bridge bridge = new CTranslate2Bridge(
                modelDirectory, settings.model().computeType(), threads)) {
            final int chunkSamples = 30 * 16000;
            final StringBuilder builder = new StringBuilder();
            for (int offset = 0; offset < audio.samples().length; offset += chunkSamples) {
                final float[] chunk = Arrays.copyOfRange(
                        audio.samples(),
                        offset,
                        Math.min(audio.samples().length, offset + chunkSamples)
                );
                final String text = bridge.transcribe(chunk, settings.language());
                builder.append(TranscriptionTextFormatter.formatLine(
                        offset * 1000L / 16000L,
                        text
                ));
            }
            formatted = builder.toString();
        }
        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        publishCTranslate2Result(formatted, audio.durationMs(), processingTimeMs);
    }

    /**
     * CTranslate2結果を保存し、既存の文字起こしイベントとして通知します。
     *
     * @param text タイムスタンプ付き本文。例: {@code "[00:00.000] こんにちは"}
     * @param durationMs 音声長。例: {@code 5000L}
     * @param processingTimeMs 推論時間。例: {@code 800L}
     */
    private void publishCTranslate2Result(
            final String text,
            final long durationMs,
            final long processingTimeMs
    ) {
        try {
            TranscriptionTextRepository.saveFilteredText(context, sessionId, text);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save CTranslate2 transcription text", e);
        }
        SystemEventHub.publish(new WhisperTranscriptionEvent(
                sessionId, 0, text, false, true, null, 0, durationMs,
                processingTimeMs, settings.model().key(), WhisperTranscriptionTag.FileTranscribing));
    }

    /**
     * ファイル文字起こし用の推論設定を作り、内蔵VADを有効化します。
     *
     * @param vadModelPath VADモデルの実ファイルパス。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @return VADが有効な推論設定。例: {@code params.vad == true}
     * @throws IllegalArgumentException vadModelPathがnullまたは空文字の場合
     */
    @NonNull
    private WhisperBridge.FullParams createFullParams(@NonNull final String vadModelPath) {
        final WhisperBridge.FullParams params =
                WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY);
        params.printProgress = false;
        params.printSpecial = false;
        params.printRealtime = false;
        params.printTimestamps = true;
        params.noContext = settings.noContext();
        params.language = settings.language();
        params.nThreads = Math.min(
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        return WhisperVadConfig.enable(params, vadModelPath);
    }

    private void publishResult(final long whisperContext, final long durationMs,
            final long processingTimeMs) {
        final FileTranscriptionText transcriptionText = collectTimestampedText(whisperContext);
        try {
            TranscriptionTextRepository.saveFilteredText(
                    context,
                    sessionId,
                    transcriptionText.text
            );
        } catch (IOException e) {
            Log.e(TAG, "Failed to save file transcription text", e);
        }

        SystemEventHub.publish(new WhisperTranscriptionEvent(sessionId, 0, transcriptionText.text,
                transcriptionText.speakerChanged, true, null, 0, durationMs, processingTimeMs,
                settings.model().key(), WhisperTranscriptionTag.FileTranscribing));
    }

    /**
     * Whisperセグメントからタイムスタンプ付きテキストを作成します。
     *
     * @param whisperContext 推論済み Whisper context。例: {@code whisperContext}
     * @return 文字起こし本文と話者変化フラグ。例: {@code new FileTranscriptionText("[00:00.000] ...", false)}
     */
    @NonNull
    private FileTranscriptionText collectTimestampedText(final long whisperContext) {
        final int segmentCount = WhisperBridge.fullNSegments(whisperContext);
        try(PooledStringBuilder builder = ScopableUtility.getBuilder()) {
            boolean speakerChanged = false;
            for (int i = 0; i < segmentCount; i++) {
                builder.append(TranscriptionTextFormatter.formatLine(
                        WhisperBridge.fullSegmentT0(whisperContext, i) * 10L,
                        WhisperBridge.fullSegmentText(whisperContext, i)
                ));
                speakerChanged |= WhisperBridge.fullSegmentSpeakerTurnNext(whisperContext, i);
            }
            return new FileTranscriptionText(builder.toString(), speakerChanged);
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
                settings.model().key(),
                WhisperTranscriptionTag.FileTranscribing
        ));
    }

    /** ファイル文字起こしで作成したテキストと補助情報です。 */
    private static final class FileTranscriptionText {
        final String text;
        final boolean speakerChanged;

        FileTranscriptionText(final String text, final boolean speakerChanged) {
            this.text = text == null ? "" : text;
            this.speakerChanged = speakerChanged;
        }
    }
}
