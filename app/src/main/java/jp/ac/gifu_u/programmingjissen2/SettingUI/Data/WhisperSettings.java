package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

/** Whisper 推論に関係するユーザー設定です。 */
public final class WhisperSettings {
    public static final WhisperModelOption DEFAULT_MODEL = WhisperModelOption.CT2_SMALL_INT8;
    public static final String DEFAULT_LANGUAGE = WhisperLanguageOption.JAPANESE.value();
    public static final int DEFAULT_WINDOW_MS = 5000;
    public static final int DEFAULT_OVERLAP_MS = 1000;
    public static final int DEFAULT_MIN_FINAL_MS = 1000;
    public static final int DEFAULT_MAX_THREADS = 4;
    public static final boolean DEFAULT_AUDIO_RECORDING_ENABLED = false;
    public static final boolean DEFAULT_AUTO_RETRANSCRIBE_ENABLED = false;
    public static final boolean DEFAULT_VAD_ENABLED = true;
    public static final float DEFAULT_VAD_THRESHOLD = 0.1f;
    public static final boolean DEFAULT_TRANSLATE_TO_ENGLISH = false;
    public static final String DEFAULT_PROMPT = "";

    private final ITranscriptionModel model;
    private final String language;
    private final int windowMs;
    private final int overlapMs;
    private final int minFinalMs;
    private final int maxThreads;
    private final boolean audioRecordingEnabled;
    private final boolean autoRetranscribeEnabled;
    private final boolean vadEnabled;
    private final float vadThreshold;
    private final boolean translateToEnglish;
    private final String prompt;
    private final FileTranscriptionSettings fileTranscription;

    /**
     * リアルタイム設定と独立したファイル一括設定を作成します。
     *
     * @param model CTranslate2モデル。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @param language リアルタイム認識言語。例: {@code "ja"}
     * @param windowMs 推論窓ms。例: {@code 5000}
     * @param overlapMs 重なりms。例: {@code 1000}
     * @param minFinalMs 最終推論の最小長ms。例: {@code 1000}
     * @param maxThreads リアルタイム最大スレッド数。例: {@code 4}
     * @param audioRecordingEnabled WAV保存ならtrue。例: {@code true}
     * @param autoRetranscribeEnabled 終了後に一括再推論するならtrue。例: {@code true}
     * @param vadEnabled CTranslate2 VADを使うならtrue。例: {@code true}
     * @param vadThreshold VADの発話確率閾値。例: {@code 0.6f}
     * @param translateToEnglish リアルタイム英語翻訳ならtrue。例: {@code false}
     * @param prompt リアルタイムinitial prompt。例: {@code "専門用語"}
     * @param fileTranscription Whisper.cpp一括設定。例:
     *                          {@code FileTranscriptionSettings.defaultSettings()}
     */
    public WhisperSettings(
            final ITranscriptionModel model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean audioRecordingEnabled,
            final boolean autoRetranscribeEnabled,
            final boolean vadEnabled,
            final float vadThreshold,
            final boolean translateToEnglish,
            final String prompt,
            final FileTranscriptionSettings fileTranscription
    ) {
        this.model = model == null ? DEFAULT_MODEL : model;
        this.language = normalizeLanguage(language);
        this.windowMs = clamp(windowMs, 1000, 30000);
        this.overlapMs = clamp(overlapMs, 0, Math.max(0, this.windowMs - 250));
        this.minFinalMs = clamp(minFinalMs, 250, this.windowMs);
        this.maxThreads = clamp(maxThreads, 1, 8);
        this.audioRecordingEnabled = audioRecordingEnabled;
        this.autoRetranscribeEnabled = audioRecordingEnabled && autoRetranscribeEnabled;
        this.vadEnabled = vadEnabled;
        this.vadThreshold = Math.max(0.0f, Math.min(1.0f, vadThreshold));
        this.translateToEnglish = translateToEnglish;
        final String promptValue = prompt == null ? DEFAULT_PROMPT : prompt.trim();
        this.prompt = promptValue.length() <= 300
                ? promptValue : promptValue.substring(0, 300);
        this.fileTranscription = fileTranscription == null
                ? FileTranscriptionSettings.defaultSettings()
                : fileTranscription;
    }

    /**
     * 既定設定を作成します。
     *
     * @return small int8・日本語・VAD有効の新しい設定。例: {@code WhisperSettings}
     */
    @NonNull
    @Contract(" -> new")
    public static WhisperSettings defaultSettings() {
        return new WhisperSettings(
                DEFAULT_MODEL,
                DEFAULT_LANGUAGE,
                DEFAULT_WINDOW_MS,
                DEFAULT_OVERLAP_MS,
                DEFAULT_MIN_FINAL_MS,
                DEFAULT_MAX_THREADS,
                DEFAULT_AUDIO_RECORDING_ENABLED,
                DEFAULT_AUTO_RETRANSCRIBE_ENABLED,
                DEFAULT_VAD_ENABLED,
                DEFAULT_VAD_THRESHOLD,
                DEFAULT_TRANSLATE_TO_ENGLISH,
                DEFAULT_PROMPT,
                FileTranscriptionSettings.defaultSettings()
        );
    }

    /**
     * ファイル一括設定だけを差し替えます。
     *
     * @param value 新しい一括設定。例: {@code FileTranscriptionSettings.defaultSettings()}
     * @return リアルタイム設定を維持した新しい設定。例: {@code WhisperSettings}
     */
    @NonNull
    public WhisperSettings withFileTranscription(
            @NonNull final FileTranscriptionSettings value
    ) {
        return new WhisperSettings(
                model, language, windowMs, overlapMs, minFinalMs, maxThreads,
                audioRecordingEnabled, autoRetranscribeEnabled, vadEnabled, vadThreshold,
                translateToEnglish, prompt, value
        );
    }

    /**
     * クイックスタートで変更できるリアルタイム設定を差し替えます。
     *
     * @param newWindowMs 推論窓ms。例: {@code 5000}
     * @param newVadThreshold VAD発話確率閾値。例: {@code 0.6f}
     * @param recordingEnabled WAVを保存するならtrue。例: {@code true}
     * @param retranscribeEnabled 録音終了後に再推論するならtrue。例: {@code true}
     * @return その他の設定を維持した新しい設定。例: {@code WhisperSettings}
     */
    @NonNull
    public WhisperSettings withQuickSettings(
            final int newWindowMs,
            final float newVadThreshold,
            final boolean recordingEnabled,
            final boolean retranscribeEnabled
    ) {
        return new WhisperSettings(
                model, language, newWindowMs, overlapMs, minFinalMs, maxThreads,
                recordingEnabled, retranscribeEnabled, vadEnabled, newVadThreshold,
                translateToEnglish, prompt, fileTranscription
        );
    }

    public ITranscriptionModel model() { return model; }
    public String language() { return language; }
    public int windowMs() { return windowMs; }
    public int overlapMs() { return overlapMs; }
    public int minFinalMs() { return minFinalMs; }
    public int maxThreads() { return maxThreads; }
    public boolean audioRecordingEnabled() { return audioRecordingEnabled; }
    public boolean autoRetranscribeEnabled() { return autoRetranscribeEnabled; }
    public boolean vadEnabled() { return vadEnabled; }
    public float vadThreshold() { return vadThreshold; }
    public boolean translateToEnglish() { return translateToEnglish; }
    public String prompt() { return prompt; }
    public FileTranscriptionSettings fileTranscription() { return fileTranscription; }

    /**
     * 言語保存値をUIで選択可能な値へ正規化します。
     *
     * @param value 言語コード。例: {@code "ja"}
     * @return 正規化済みコード。例: {@code "ja"}
     */
    private static String normalizeLanguage(final String value) {
        return WhisperLanguageOption.fromValue(value).value();
    }

    /**
     * 整数を指定範囲へ収めます。
     *
     * @param value 対象値。例: {@code 12}
     * @param min 下限。例: {@code 1}
     * @param max 上限。例: {@code 8}
     * @return 範囲内の値。例: {@code 8}
     */
    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
