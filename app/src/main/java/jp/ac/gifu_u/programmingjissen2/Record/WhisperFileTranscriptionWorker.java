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
import jp.ac.gifu_u.programmingjissen2.Transcription.TranscriptionWorkerResult;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperCPPTranscriptionWorker;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperVadConfig;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextRepository;
import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.TranscriptionJsonWriter;

/** 音声ファイルを読み、Whisper.cpp専用workerへ全PCMを一度に渡す呼び出しworkerです。 */
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
        try {
            SystemEventHub.publish(new WhisperProgressEvent(
                    sessionId, WhisperProgressEvent.Phase.FILE_READING, 0, 0));
            final DecodedAudio audio = AudioFilePcmDecoder.decodeToWhisperPcm(context, audioUri);
            SystemEventHub.publish(new WhisperProgressEvent(
                    sessionId,
                    WhisperProgressEvent.Phase.FILE_TRANSCRIBING,
                    0,
                    audio.durationMs()
            ));
            final String modelPath = MyUtils.prepareModelPath(
                    context, settings.fileTranscription().model().assetName());
            final String vadModelPath = MyUtils.prepareModelPath(
                    context, WhisperVadConfig.MODEL_ASSET_NAME);
            transcribeWholeAudio(modelPath, vadModelPath, audio);
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
     * デコード済みPCM全体をWhisper.cpp専用workerへ渡します。
     * @param modelPath ggmlモデル。例: {@code "/data/.../ggml-small_q8_0.bin"}
     * @param vadModelPath VADモデル。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param audio 音声全体。例: {@code decodedAudio}
     * @throws IOException モデル読み込み・推論・結果保存に失敗した場合
     */
    private void transcribeWholeAudio(
            @NonNull final String modelPath,
            @NonNull final String vadModelPath,
            @NonNull final DecodedAudio audio
    ) throws IOException {
        final long startedAt = System.nanoTime();
        final TranscriptionWorkerResult result;
        try (WhisperCPPTranscriptionWorker worker = new WhisperCPPTranscriptionWorker(
                modelPath, vadModelPath, settings)) {
            result = worker.transcribe(audio.samples(), true);
        }
        final long processingTimeMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);
        final WhisperTranscriptionEvent event = new WhisperTranscriptionEvent(
                sessionId, 0, result.text, result.speakerChanged, true, null,
                0, audio.durationMs(), processingTimeMs,
                settings.fileTranscription().model().key(),
                WhisperTranscriptionTag.FileTranscribing
        );
        final TranscriptionJsonWriter jsonWriter =
                new TranscriptionJsonWriter(context, sessionId);
        jsonWriter.append(event);
        jsonWriter.finish();
        TranscriptionTextRepository.saveFilteredText(context, sessionId, result.text);
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
