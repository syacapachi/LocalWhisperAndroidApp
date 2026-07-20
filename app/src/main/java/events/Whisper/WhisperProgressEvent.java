package events.Whisper;

import androidx.annotation.NonNull;

/** 推論開始時点の進捗をSystemEventHub経由でUIへ通知します。 */
public record WhisperProgressEvent(
        @NonNull String sessionId,
        @NonNull Phase phase,
        long startMs,
        long durationMs
) {
    /** UIへ表示する処理段階です。 */
    public enum Phase {
        REALTIME_INFERENCE,
        FILE_READING,
        FILE_TRANSCRIBING
    }
}
