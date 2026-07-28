package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

import java.util.Objects;

/** JSONに保存される外部モデル定義です。 */
public final class ExternalTranscriptionModel implements ITranscriptionModel {
    private final String key;
    private final String name;
    private final String modelPath;
    private final WhisperInferenceEngine engine;
    private final String computeType;

    /**
     * 外部モデル定義を作成します。
     * @param key 一意な保存キー。例: {@code "external-a12b"}
     * @param name UI名。例: {@code "会議用small"}
     * @param modelPath ファイルまたはディレクトリの絶対パス。例: {@code "/sdcard/model.bin"}
     * @param engine 実行方式。例: {@code WhisperInferenceEngine.WHISPER_CPP}
     * @param computeType CTranslate2計算型。例: {@code "int8"}
     * @throws IllegalArgumentException key、name、modelPathが空の場合
     */
    public ExternalTranscriptionModel(
            @NonNull final String key,
            @NonNull final String name,
            @NonNull final String modelPath,
            @NonNull final WhisperInferenceEngine engine,
            @NonNull final String computeType
    ) {
        if (key.trim().isEmpty() || name.trim().isEmpty() || modelPath.trim().isEmpty()) {
            throw new IllegalArgumentException("External model fields must not be empty");
        }
        this.key = key.trim();
        this.name = name.trim();
        this.modelPath = modelPath.trim();
        this.engine = Objects.requireNonNull(engine);
        this.computeType = computeType.trim();
    }

    @Override @NonNull public String key() { return key; }
    @Override @NonNull public String label() { return name; }
    @Override @NonNull public String modelPath() { return modelPath; }
    @Override @NonNull public WhisperInferenceEngine engine() { return engine; }
    @Override @NonNull public String computeType() { return computeType; }
    @Override public boolean bundled() { return false; }

    /** @return 名前・実行方式・パスを含むUI表示。例: {@code "会議用small（Whisper.cpp）"} */
    @Override
    @NonNull
    public String displayName() {
        return name + "（" + (engine == WhisperInferenceEngine.WHISPER_CPP
                ? "Whisper.cpp" : "CTranslate2") + "）\n" + modelPath;
    }

    /** @return {@link #displayName()}と同じ表示文字列 */
    @Override @NonNull public String toString() { return displayName(); }
}
