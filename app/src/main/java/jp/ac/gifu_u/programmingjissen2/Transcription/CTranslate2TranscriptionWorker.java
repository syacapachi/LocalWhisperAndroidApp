package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import CTranslate2.CTranslate2Bridge;
import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.Transcription.Prompt.PreviousContextWordExtractor;

/** リアルタイム音声窓をCTranslate2で推論し、直前文脈を管理するworkerです。 */
public final class CTranslate2TranscriptionWorker implements AutoCloseable {
    private static final int PREVIOUS_CONTEXT_MAX_WORDS = 24;
    private static final int PREVIOUS_CONTEXT_MAX_CODE_POINTS = 100;
    private final CTranslate2Bridge bridge;
    private final WhisperSettings settings;
    private String previousWordContext = "";

    /**
     * CTranslate2モデルを開きます。
     * @param modelDirectory 変換済みモデル。例: {@code "/data/.../openai-whisper-small-int8"}
     * @param vadModelPath Silero VADモデル。無効時はnull。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
     * @throws IllegalStateException CTranslate2モデルまたはSilero VADモデルを読み込めない場合
     */
    public CTranslate2TranscriptionWorker(
            @NonNull final String modelDirectory,
            final String vadModelPath,
            @NonNull final WhisperSettings settings
    ) {
        this.settings = settings;
        final int threads = Math.min(
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        bridge = new CTranslate2Bridge(
                modelDirectory,
                settings.model().computeType(),
                vadModelPath,
                threads
        );
    }

    /**
     * リングバッファ上の最大2区間を連続音声として推論します。
     * @param samples Direct PCM16保存領域。例: {@code ByteBuffer.allocateDirect(320000)}
     * @param firstByteOffset 第1区間byte位置。例: {@code 160000}
     * @param firstSampleCount 第1区間数。例: {@code 40000}
     * @param secondByteOffset 第2区間byte位置。例: {@code 0}
     * @param secondSampleCount 第2区間数。例: {@code 40000}
     * @return 本文と話者情報。例: {@code new TranscriptionWorkerResult("こんにちは", false)}
     * @throws IllegalArgumentException Directバッファまたは区間が不正な場合
     * @throws IllegalStateException native推論に失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(
            @NonNull final ByteBuffer samples,
            final int firstByteOffset,
            final int firstSampleCount,
            final int secondByteOffset,
            final int secondSampleCount
    ) {
        final String prompt;
        if (previousWordContext.isEmpty()) {
            prompt = settings.prompt();
        } else if (settings.prompt().isEmpty()) {
            prompt = previousWordContext;
        } else {
            prompt = StringBufferBuilderPool.Join(
                    "\n", settings.prompt(), previousWordContext);
        }
        final String text = bridge.transcribe(
                samples,
                firstByteOffset,
                firstSampleCount,
                secondByteOffset,
                secondSampleCount,
                settings.language(),
                settings.translateToEnglish(),
                prompt,
                settings.vadEnabled(),
                settings.vadThreshold()
        );
        if (!text.trim().isEmpty()) {
            previousWordContext = PreviousContextWordExtractor.extractRecentWords(
                    text,
                    settings.language(),
                    PREVIOUS_CONTEXT_MAX_WORDS,
                    PREVIOUS_CONTEXT_MAX_CODE_POINTS
            );
        }
        return new TranscriptionWorkerResult(text, false);
    }

    /** CTranslate2モデルを解放します。複数回呼んでも安全です。 */
    @Override
    public void close() {
        bridge.close();
    }

}
