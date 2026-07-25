package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

/** assets同梱モデルと外部モデルを同じUI・設定で扱うための共通定義です。 */
public interface ITranscriptionModel {
    /** @return 保存キー。例: {@code "ct2-openai-small-int8"} */
    @NonNull String key();

    /** @return 利用者向けの名前。例: {@code "会議用small"} */
    @NonNull String label();

    /** @return assets相対パスまたは外部絶対パス。例: {@code "/sdcard/models/model.bin"} */
    @NonNull String modelPath();

    /** @return 実行エンジン。例: {@code WhisperInferenceEngine.WHISPER_CPP} */
    @NonNull WhisperInferenceEngine engine();

    /** @return CTranslate2計算型。Whisper.cppでは空文字。例: {@code "int8"} */
    @NonNull String computeType();

    /** @return APKのassetsに同梱されている場合true。例: {@code true} */
    boolean bundled();

    /** @return UI表示名。例: {@code "会議用small（CTranslate2）"} */
    @NonNull String displayName();
}
