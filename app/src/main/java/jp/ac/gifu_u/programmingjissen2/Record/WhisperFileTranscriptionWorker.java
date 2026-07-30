package jp.ac.gifu_u.programmingjissen2.Record;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import Utils.MyUtils;
import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;
import events.Whisper.WhisperTranscriptionEvent;
import events.Whisper.WhisperTranscriptionTag;
import events.Whisper.WhisperProgressEvent;
import jp.ac.gifu_u.programmingjissen2.Record.Utility.AudioFilePcmDecoder;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionWindowLimits;
import jp.ac.gifu_u.programmingjissen2.SettingUI.ModelPathResolver;
import jp.ac.gifu_u.programmingjissen2.Transcription.TranscriptionWorkerResult;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperCPPTranscriptionWorker;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperVadConfig;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextRepository;
import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.TranscriptionJsonWriter;

/** 音声ファイルを一定時間ずつデコードし、同じWhisper.cpp contextで順次推論するworkerです。 */
public final class WhisperFileTranscriptionWorker implements Runnable {
    private static final String TAG = WhisperFileTranscriptionWorker.class.getSimpleName();

    private final Context context;
    private final Uri audioUri;
    private final String sessionId;
    private final WhisperSettings settings;
    private final CompletionListener completionListener;
    private Thread workerThread;

    /** ファイル文字起こし完了時に呼ばれるlistenerです。 */
    public interface CompletionListener {
        /**
         * ファイル推論の終了を通知します。
         * @param sessionId session ID。例: {@code "file-abc"}
         * @param errorMessage 成功時は空文字。例: {@code "audio track not found"}
         */
        void onComplete(String sessionId, String errorMessage);
    }

    /**
     * ファイル推論の呼び出しworkerを作成します。
     * @param context assetsと音声URIを読むContext。例: {@code activity}
     * @param audioUri 対象音声。例: {@code content://media/...}
     * @param sessionId 結果ID。例: {@code "file-1a2b"}
     * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
     * @param completionListener 完了通知先。例: {@code this::onFileTranscriptionComplete}
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
     * ファイル推論スレッドを開始します。
     * @return 開始できた場合true。例: {@code true}
     */
    public synchronized boolean start() {
        if (isAlive()) {
            return false;
        }
        workerThread = StringBufferBuilderPool.NewThreadWithPoolCleanup(
                this,
                "WhisperFileTranscriptionWorker"
        );
        workerThread.start();
        return true;
    }

    /** @return workerが生存中ならtrue。例: {@code false} */
    public boolean isAlive() {
        final Thread thread = workerThread;
        return thread != null && thread.isAlive();
    }

    /** 実音声をデコードし、Whisper.cpp一括推論結果をイベントへ変換します。例外はエラーイベントへ変換します。 */
    @Override
    public void run() {
        String errorMessage = "";
        TranscriptionJsonWriter jsonWriter = null;
        try {
            SystemEventHub.publish(new WhisperProgressEvent(
                    sessionId, WhisperProgressEvent.Phase.FILE_READING, 0, 0));
            final String modelPath = ModelPathResolver.resolve(
                    context, settings.fileTranscription().model());
            final String vadModelPath = MyUtils.prepareModelPath(
                    context, WhisperVadConfig.MODEL_ASSET_NAME);
            jsonWriter = new TranscriptionJsonWriter(
                    context,
                    sessionId,
                    settings,
                    WhisperTranscriptionTag.FileTranscribing
            );
            TranscriptionTextRepository.clearFilteredText(context, sessionId);
            final TranscriptionJsonWriter activeWriter = jsonWriter;
            final int[] sequence = {0};
            try (WhisperCPPTranscriptionWorker worker =
                         new WhisperCPPTranscriptionWorker(modelPath, vadModelPath, settings)) {
                AudioFilePcmDecoder.decodeToWhisperPcm(
                        context,
                        audioUri,
                        Math.min(
                                settings.fileTranscription().windowMs(),
                                FileTranscriptionWindowLimits.maxSeconds() * 1000),
                        (audio, startSample, finalChunk) -> transcribeChunk(
                                worker,
                                activeWriter,
                                audio,
                                startSample,
                                sequence[0]++,
                                finalChunk
                        )
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "File transcription failed", e);
            errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            publishError(errorMessage);
        } finally {
            if (jsonWriter != null) {
                jsonWriter.finish();
            }
            workerThread = null;
            if (completionListener != null) {
                completionListener.onComplete(sessionId, errorMessage);
            }
        }
    }

    /**
     * 1つのPCMチャンクを推論し、JSON・テキスト・イベントへ逐次出力します。
     * @param worker ファイル全体で再利用するWhisper worker。例: {@code worker}
     * @param jsonWriter 同一sessionのJSON出力先。例: {@code jsonWriter}
     * @param audio 16kHz PCMチャンク。例: {@code decodedAudio}
     * @param startSample ファイル先頭からのサンプル位置。例: {@code 480000}
     * @param sequence 結果番号。例: {@code 1}
     * @param finalChunk 最後のチャンクならtrue。例: {@code false}
     * @throws IOException 推論またはテキスト保存に失敗した場合
     */
    private void transcribeChunk(
            @NonNull final WhisperCPPTranscriptionWorker worker,
            @NonNull final TranscriptionJsonWriter jsonWriter,
            @NonNull final DecodedAudio audio,
            final long startSample,
            final int sequence,
            final boolean finalChunk
    ) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("File transcription was interrupted");
        }
        final long startMs = startSample * 1000L / audio.sampleRate();
        SystemEventHub.publish(new WhisperProgressEvent(
                sessionId,
                WhisperProgressEvent.Phase.FILE_TRANSCRIBING,
                startMs,
                audio.durationMs()
        ));
        final long startedAt = System.nanoTime();
        final TranscriptionWorkerResult result = worker.transcribe(
                audio.samples(),
                0,
                audio.sampleCount(),
                0,
                0,
                true,
                startMs);
        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        final WhisperTranscriptionEvent event = new WhisperTranscriptionEvent(
                sessionId, sequence, result.text, result.speakerChanged, finalChunk, null,
                startMs, audio.durationMs(), processingTimeMs,
                settings.fileTranscription().model().key(),
                WhisperTranscriptionTag.FileTranscribing
        );
        jsonWriter.append(event);
        TranscriptionTextRepository.appendFilteredText(context, sessionId, result.text);
        SystemEventHub.publish(event);
    }

    /**
     * エラーイベントを発行します。
     * @param message 原因。例: {@code "model load failed"}
     */
    private void publishError(final String message) {
        SystemEventHub.publish(new WhisperTranscriptionEvent(
                sessionId, 0, "", false, true,
                StringBufferBuilderPool.Join("", "ファイル文字起こしに失敗しました: ", message),
                0, 0, 0, settings.fileTranscription().model().key(),
                WhisperTranscriptionTag.FileTranscribing
        ));
    }
}
