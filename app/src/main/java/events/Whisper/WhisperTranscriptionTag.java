package events.Whisper;

/** Whisper 文字起こしイベントがどの処理状態から発行されたかを表すタグです。 */
public enum WhisperTranscriptionTag {
    Recording,
    FileTranscribing,
    Stopping
}
