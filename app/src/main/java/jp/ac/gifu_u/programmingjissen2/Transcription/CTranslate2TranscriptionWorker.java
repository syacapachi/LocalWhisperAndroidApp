package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import CTranslate2.CTranslate2Bridge;
import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** リアルタイム音声窓をCTranslate2で推論し、直前文脈を管理するworkerです。 */
public final class CTranslate2TranscriptionWorker implements AutoCloseable {
    private final CTranslate2Bridge bridge;
    private final WhisperSettings settings;
    private String previousContext = "";

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
     * 1つの音声窓を推論し、空でない結果の末尾100文字を次回文脈として保存します。
     * @param samples 16kHzモノラルfloat PCM。例: {@code new float[80000]}
     * @return 本文と話者情報。例: {@code new TranscriptionWorkerResult("こんにちは", false)}
     * @throws IllegalStateException native推論に失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(@NonNull final float[] samples) {
        final String prompt = previousContext.isEmpty()
                ? settings.prompt()
                : StringBufferBuilderPool.Join("\n", settings.prompt(), previousContext);
        final String text = bridge.transcribe(
                samples,
                settings.language(),
                settings.translateToEnglish(),
                prompt,
                settings.vadEnabled(),
                settings.vadThreshold()
        );
        if (text != null && !text.trim().isEmpty()) {
            previousContext = takeLastCodePoints(text.trim(), 100);
        }
        return new TranscriptionWorkerResult(text, false);
    }

    /** CTranslate2モデルを解放します。複数回呼んでも安全です。 */
    @Override
    public void close() {
        bridge.close();
    }

    /**
     * 文字列末尾をUnicode code point単位で切り出します。
     * @param text 元文字列。例: {@code "前の文字起こし"}
     * @param maxCodePoints 最大文字数。例: {@code 100}
     * @return 末尾文字列。例: {@code "文字起こし"}。null時は空文字で例外はありません
     */
    @NonNull
    public static String takeLastCodePoints(final String text, final int maxCodePoints) {
        if (text == null || text.isEmpty() || maxCodePoints <= 0) {
            return "";
        }
        final int count = text.codePointCount(0, text.length());
        if (count <= maxCodePoints) {
            return text;
        }
        return text.substring(text.offsetByCodePoints(0, count - maxCodePoints));
    }
}
