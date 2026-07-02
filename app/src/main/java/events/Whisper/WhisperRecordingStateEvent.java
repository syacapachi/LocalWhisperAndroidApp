package events.Whisper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Foreground service 側の録音状態を UI へ通知するイベントです。 */
public record WhisperRecordingStateEvent(
        @Nullable String sessionId,
        boolean recording,
        boolean stopping,
        @NonNull String message,
        @NonNull String latestText,
        @NonNull String modelKey
) {
}
