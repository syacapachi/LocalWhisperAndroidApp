package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

/** Whisper.cppによるファイル・録音全体の一括文字起こし設定です。 */
public final class FileTranscriptionSettings {
    public static final WhisperCppModelOption DEFAULT_MODEL = WhisperCppModelOption.SMALL_Q8_0;
    public static final boolean DEFAULT_VAD_ENABLED = true;
    public static final float DEFAULT_VAD_THRESHOLD = 0.5f;
    public static final String DEFAULT_PROMPT = "";

    private final ITranscriptionModel model;
    private final String language;
    private final int maxThreads;
    private final boolean useGpu;
    private final boolean vadEnabled;
    private final float vadThreshold;
    private final boolean translateToEnglish;
    private final String prompt;

    /**
     * ファイル一括推論設定を作成し、数値とプロンプトを有効範囲へ補正します。
     * @param model Whisper.cppモデル。例: {@code WhisperCppModelOption.SMALL_Q8_0}
     * @param language 言語。例: {@code "ja"}
     * @param maxThreads 最大スレッド数。例: {@code 4}
     * @param useGpu GPUを利用するならtrue。例: {@code false}
     * @param vadEnabled Silero VADを使うならtrue。例: {@code true}
     * @param vadThreshold 発話確率閾値。例: {@code 0.5f}
     * @param translateToEnglish 英語翻訳ならtrue。例: {@code false}
     * @param prompt initial prompt。例: {@code "岐阜大学 CTranslate2"}
     */
    public FileTranscriptionSettings(
            final ITranscriptionModel model,
            final String language,
            final int maxThreads,
            final boolean useGpu,
            final boolean vadEnabled,
            final float vadThreshold,
            final boolean translateToEnglish,
            final String prompt
    ) {
        this.model = model == null ? DEFAULT_MODEL : model;
        this.language = WhisperLanguageOption.fromValue(language).value();
        this.maxThreads = Math.max(1, Math.min(8, maxThreads));
        this.useGpu = useGpu;
        this.vadEnabled = vadEnabled;
        this.vadThreshold = Math.max(0.0f, Math.min(1.0f, vadThreshold));
        this.translateToEnglish = translateToEnglish;
        final String value = prompt == null ? DEFAULT_PROMPT : prompt.trim();
        this.prompt = value.length() <= 300 ? value : value.substring(0, 300);
    }

    /**
     * 既定設定を作成します。
     * @return small Q8_0・日本語・VAD有効の設定。例: {@code FileTranscriptionSettings}
     */
    @NonNull
    public static FileTranscriptionSettings defaultSettings() {
        return new FileTranscriptionSettings(
                DEFAULT_MODEL,
                WhisperSettings.DEFAULT_LANGUAGE,
                WhisperSettings.DEFAULT_MAX_THREADS,
                WhisperSettings.DEFAULT_USE_GPU,
                DEFAULT_VAD_ENABLED,
                DEFAULT_VAD_THRESHOLD,
                WhisperSettings.DEFAULT_TRANSLATE_TO_ENGLISH,
                DEFAULT_PROMPT
        );
    }

    public ITranscriptionModel model() { return model; }
    public String language() { return language; }
    public int maxThreads() { return maxThreads; }
    public boolean useGpu() { return useGpu; }
    public boolean vadEnabled() { return vadEnabled; }
    public float vadThreshold() { return vadThreshold; }
    public boolean translateToEnglish() { return translateToEnglish; }
    public String prompt() { return prompt; }
}
