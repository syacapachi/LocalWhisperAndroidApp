package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

/** モデルファイルを実行する推論ランタイムです。 */
public enum WhisperInferenceEngine {
    /** 単一ggmlファイルをwhisper.cppで実行します。 */
    WHISPER_CPP,
    /** 変換済みモデルディレクトリをCTranslate2で実行します。 */
    CTRANSLATE2
}
