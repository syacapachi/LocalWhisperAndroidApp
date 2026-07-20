package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

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
     * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
     * @throws IllegalStateException モデルを読み込めない場合
     */
    public CTranslate2TranscriptionWorker(
            @NonNull final String modelDirectory,
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
                threads
        );
    }

    /**
     * 1つの音声窓を推論し、結果から抽出した直近単語一覧を次回文脈として保存します。
     * @param samples 16kHzモノラルPCM16。例: {@code new short[80000]}
     * @return 本文と話者情報。例: {@code new TranscriptionWorkerResult("こんにちは", false)}
     * @throws IllegalStateException native推論に失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(@NonNull final short[] samples) {
        return transcribe(samples, samples.length);
    }

    /**
     * 再利用PCM16配列の有効部分だけを推論します。
     * @param samples PCM16出力バッファ。例: {@code new short[80000]}
     * @param sampleCount 有効サンプル数。例: {@code 64000}
     * @return 本文と話者情報。例: {@code new TranscriptionWorkerResult("こんにちは", false)}
     * @throws IllegalArgumentException sampleCountが配列範囲外の場合
     * @throws IllegalStateException native推論に失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(
            @NonNull final short[] samples,
            final int sampleCount
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
                sampleCount,
                settings.language(),
                settings.translateToEnglish(),
                prompt,
                settings.vadEnabled(),
                settings.vadThreshold()
        );
        if (text != null && !text.trim().isEmpty()) {
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
