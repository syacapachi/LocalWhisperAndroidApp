package events.Whisper;

import androidx.annotation.Nullable;

/** Foreground service 側の録音状態を UI へ通知するイベントです。 */
public record WhisperRecordingStateEvent(
        @Nullable String sessionId,
        boolean recording,
        boolean stopping,
        String message,
        String latestText,
        String modelKey
) {
}
