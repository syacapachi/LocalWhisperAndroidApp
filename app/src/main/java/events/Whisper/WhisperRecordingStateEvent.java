package events.Whisper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Foreground service側の独立した録音・推論状態をUIへ通知するイベントです。
 *
 * @param sessionId 録音ID。例: {@code "record-a1b2"}
 * @param sessionActive 録音セッション継続中ならtrue。例: {@code true}
 * @param recording マイク録音中ならtrue。例: {@code true}
 * @param stopping 録音停止後のキュー排出中ならtrue。例: {@code false}
 * @param inferenceAlive 推論workerが生存中ならtrue。例: {@code true}
 * @param inferenceAccepting 新しい音声を推論へ投入できるならtrue。例: {@code true}
 * @param message 状態メッセージ。例: {@code "バックグラウンド録音中"}
 * @param latestText 最新文字起こし。例: {@code "こんにちは"}
 * @param modelKey モデル識別子。例: {@code "base"}
 */
public record WhisperRecordingStateEvent(
        @Nullable String sessionId,
        boolean sessionActive,
        boolean recording,
        boolean stopping,
        boolean inferenceAlive,
        boolean inferenceAccepting,
        @NonNull String message,
        @NonNull String latestText,
        @NonNull String modelKey
) {
}
