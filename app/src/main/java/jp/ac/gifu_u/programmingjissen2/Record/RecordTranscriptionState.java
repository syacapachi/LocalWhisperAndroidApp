package jp.ac.gifu_u.programmingjissen2.Record;

/** 録音画面が扱う文字起こしステートです。 */
public enum RecordTranscriptionState {
    Recording,
    RecordingPaused,
    InferencePaused,
    RecordingAndInferencePaused,
    FileTranscribing,
    StopRecord
}
