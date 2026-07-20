package jp.ac.gifu_u.programmingjissen2.Transcription;

/** 推論エンジン固有workerから呼び出し側へ返す共通結果です。 */
public final class TranscriptionWorkerResult {
    /** 文字起こしまたは翻訳本文です。 */
    public final String text;
    /** この結果の直後に話者が変わった可能性がある場合trueです。 */
    public final boolean speakerChanged;

    /**
     * 共通推論結果を作成します。
     * @param text 本文。例: {@code "こんにちは"}
     * @param speakerChanged 話者変化ならtrue。例: {@code false}
     */
    public TranscriptionWorkerResult(final String text, final boolean speakerChanged) {
        this.text = text == null ? "" : text;
        this.speakerChanged = speakerChanged;
    }
}
