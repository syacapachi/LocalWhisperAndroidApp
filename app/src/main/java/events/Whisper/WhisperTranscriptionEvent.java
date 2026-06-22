package events.Whisper;

import androidx.annotation.Nullable;

/**
 * Whisper の推論結果を UI 側へ通知するためのイベントです。
 *
 * @param sessionId 録音開始ごとに作る識別 ID
 * @param sequence 同じ session 内での結果番号
 * @param text 文字起こし結果
 * @param speakerChanged この結果の直後に話者が変わった可能性がある場合 true
 * @param finalResult 録音停止時の最後の結果なら true
 * @param errorMessage エラーがある場合のメッセージ
 * @param startMs この結果が対象にしている音声の開始位置。ミリ秒
 * @param durationMs この結果が対象にしている音声の長さ。ミリ秒
 * @param processingTimeMs Whisper 推論 1 回にかかった処理時間。ミリ秒
 * @param modelKey 推論に使ったモデルの識別子
 */
public record WhisperTranscriptionEvent(
        String sessionId,
        int sequence,
        String text,
        boolean speakerChanged,
        boolean finalResult,
        @Nullable String errorMessage,
        long startMs,
        long durationMs,
        long processingTimeMs,
        String modelKey
) {
    /**
     * このイベントがエラー通知かどうかを返します。
     *
     * @return エラーメッセージがある場合 true
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
}
