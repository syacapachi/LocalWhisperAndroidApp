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
    public static final boolean DEFAULT_NO_CONTEXT = false;
    public static final boolean DEFAULT_PRINT_TIMESTAMPS = false;
    public static final boolean DEFAULT_USE_GPU = false;
    public static final boolean DEFAULT_AUDIO_RECORDING_ENABLED = false;
    public static final boolean DEFAULT_AUTO_RETRANSCRIBE_ENABLED = false;
    public static final boolean DEFAULT_VAD_ENABLED = true;
    public static final float DEFAULT_VAD_THRESHOLD = 0.6f;
    public static final float DEFAULT_SILERO_VAD_THRESHOLD = 0.5f;
    public static final boolean DEFAULT_TRANSLATE_TO_ENGLISH = false;
    public static final String DEFAULT_PROMPT = "";
    private final WhisperModelOption model;
    private final String language;
    private final int windowMs;
    private final int overlapMs;
    private final int minFinalMs;
    private final int maxThreads;
    private final boolean noContext;
    private final boolean printTimestamps;
    private final boolean useGpu;
    private final boolean audioRecordingEnabled;
    private final boolean autoRetranscribeEnabled;
    private final boolean vadEnabled;
    private final float vadThreshold;
    private final float sileroVadThreshold;
    private final boolean translateToEnglish;
    private final String prompt;
    private final FileTranscriptionSettings fileTranscription;

    /**
     * Whisper設定を作成します。数値は対応範囲へ補正され、自動再推論は音声記録OFF時にOFFになります。
     *
     * @param model モデル。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @param language 言語。例: {@code "ja"}
     * @param windowMs 推論窓ms。例: {@code 5000}
     * @param overlapMs 重なりms。例: {@code 1000}
     * @param minFinalMs 最終推論の最小ms。例: {@code 1000}
     * @param maxThreads 最大スレッド数。例: {@code 4}
     * @param noContext 文脈を引き継がない場合true。例: {@code true}
     * @param printTimestamps タイムスタンプを出す場合true。例: {@code false}
     * @param useGpu GPUを使う場合true。例: {@code false}
     * @param audioRecordingEnabled WAV保存する場合true。例: {@code true}
     * @param autoRetranscribeEnabled 停止後に再推論する場合true。例: {@code true}
     */
    public WhisperSettings(
            final WhisperModelOption model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean noContext,
            final boolean printTimestamps,
            final boolean useGpu,
            final boolean audioRecordingEnabled,
            final boolean autoRetranscribeEnabled
    ) {
        this(model, language, windowMs, overlapMs, minFinalMs, maxThreads, noContext,
                printTimestamps, useGpu, audioRecordingEnabled,
                autoRetranscribeEnabled, DEFAULT_VAD_ENABLED, DEFAULT_VAD_THRESHOLD,
                DEFAULT_TRANSLATE_TO_ENGLISH, DEFAULT_PROMPT, DEFAULT_SILERO_VAD_THRESHOLD);
    }

    /**
     * CTranslate2用設定を作成します。数値とプロンプトは対応範囲へ補正します。
     *
     * @param model CT2モデル。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @param language 文字起こし言語。例: {@code "ja"}
     * @param windowMs 推論窓ms。例: {@code 5000}
     * @param overlapMs 重なりms。例: {@code 1000}
     * @param minFinalMs 最終推論の最小ms。例: {@code 1000}
     * @param maxThreads CPUスレッド数。例: {@code 4}
     * @param noContext 旧設定互換引数。例: {@code false}
     * @param printTimestamps 旧設定互換引数。例: {@code false}
     * @param useGpu 旧設定互換引数。例: {@code false}
     * @param audioRecordingEnabled WAV保存ならtrue。例: {@code true}
     * @param autoRetranscribeEnabled 停止後の再推論ならtrue。例: {@code false}
     * @param vadEnabled CT2無音判定を使うならtrue。例: {@code true}
     * @param vadThreshold 無音確率の閾値。例: {@code 0.6f}
     * @param translateToEnglish 英語翻訳モードならtrue。例: {@code false}
     * @param prompt ユーザープロンプト。例: {@code "専門用語: CTranslate2"}
     */
    public WhisperSettings(
            final WhisperModelOption model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean noContext,
            final boolean printTimestamps,
            final boolean useGpu,
            final boolean audioRecordingEnabled,
            final boolean autoRetranscribeEnabled,
            final boolean vadEnabled,
            final float vadThreshold,
            final boolean translateToEnglish,
            final String prompt
    ) {
        this(model, language, windowMs, overlapMs, minFinalMs, maxThreads, noContext,
                printTimestamps, useGpu, audioRecordingEnabled,
                autoRetranscribeEnabled, vadEnabled, vadThreshold, translateToEnglish, prompt,
                DEFAULT_SILERO_VAD_THRESHOLD);
    }

    /**
     * CTranslate2とWhisper.cpp Silero VADの設定を作成します。数値は対応範囲へ補正します。
     *
     * @param model モデル。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @param language 文字起こし言語。例: {@code "ja"}
     * @param windowMs 推論窓ms。例: {@code 5000}
     * @param overlapMs 重なりms。例: {@code 1000}
     * @param minFinalMs 最終推論の最小ms。例: {@code 1000}
     * @param maxThreads CPUスレッド数。例: {@code 4}
     * @param noContext 旧設定互換引数。例: {@code false}
     * @param printTimestamps 旧設定互換引数。例: {@code false}
     * @param useGpu GPU利用指定。例: {@code false}
     * @param audioRecordingEnabled WAV保存ならtrue。例: {@code true}
     * @param autoRetranscribeEnabled 停止後の再推論ならtrue。例: {@code false}
     * @param vadEnabled VADを使うならtrue。例: {@code true}
     * @param vadThreshold CTranslate2の無音確率閾値。例: {@code 0.6f}
     * @param translateToEnglish 英語翻訳モードならtrue。例: {@code false}
     * @param prompt ユーザープロンプト。例: {@code "専門用語: CTranslate2"}
     * @param sileroVadThreshold Sileroの発話確率閾値。例: {@code 0.5f}
     */
    public WhisperSettings(
            final WhisperModelOption model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean noContext,
            final boolean printTimestamps,
            final boolean useGpu,
            final boolean audioRecordingEnabled,
            final boolean autoRetranscribeEnabled,
            final boolean vadEnabled,
            final float vadThreshold,
            final boolean translateToEnglish,
            final String prompt,
            final float sileroVadThreshold
    ) {
        this(model, language, windowMs, overlapMs, minFinalMs, maxThreads, noContext,
                printTimestamps, useGpu, audioRecordingEnabled, autoRetranscribeEnabled,
                vadEnabled, vadThreshold, translateToEnglish, prompt, sileroVadThreshold,
                FileTranscriptionSettings.defaultSettings());
    }

    /**
     * リアルタイム設定と独立したファイル一括設定を作成します。
     * @param model CTranslate2モデル。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @param language リアルタイム認識言語。例: {@code "ja"}
     * @param windowMs 推論窓。例: {@code 5000}
     * @param overlapMs 重なり。例: {@code 1000}
     * @param minFinalMs 最終推論の最小長。例: {@code 1000}
     * @param maxThreads リアルタイム最大スレッド数。例: {@code 4}
     * @param noContext 旧設定互換値。例: {@code false}
     * @param printTimestamps 旧設定互換値。例: {@code false}
     * @param useGpu 旧設定互換値。例: {@code false}
     * @param audioRecordingEnabled WAV保存ならtrue。例: {@code true}
     * @param autoRetranscribeEnabled 終了後に一括再推論するならtrue。例: {@code true}
     * @param vadEnabled CTranslate2 VADを使うならtrue。例: {@code true}
     * @param vadThreshold no-speech閾値。例: {@code 0.6f}
     * @param translateToEnglish リアルタイム英語翻訳ならtrue。例: {@code false}
     * @param prompt リアルタイムinitial prompt。例: {@code "専門用語"}
     * @param sileroVadThreshold 旧保存値の互換引数。例: {@code 0.5f}
     * @param fileTranscription Whisper.cpp一括設定。例: {@code FileTranscriptionSettings.defaultSettings()}
     */
    public WhisperSettings(
            final WhisperModelOption model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean noContext,
            final boolean printTimestamps,
            final boolean useGpu,
            final boolean audioRecordingEnabled,
            final boolean autoRetranscribeEnabled,
            final boolean vadEnabled,
            final float vadThreshold,
            final boolean translateToEnglish,
            final String prompt,
            final float sileroVadThreshold,
            final FileTranscriptionSettings fileTranscription
    ) {
        this.model = model == null ? DEFAULT_MODEL : model;
        this.language = normalizeLanguage(language);
        this.windowMs = clamp(windowMs, 1000, 30000);
        this.overlapMs = clamp(overlapMs, 0, Math.max(0, this.windowMs - 250));
        this.minFinalMs = clamp(minFinalMs, 250, this.windowMs);
        this.maxThreads = clamp(maxThreads, 1, 8);
        this.noContext = false;
        this.printTimestamps = false;
        this.useGpu = false;
        this.audioRecordingEnabled = audioRecordingEnabled;
        this.autoRetranscribeEnabled = audioRecordingEnabled && autoRetranscribeEnabled;
        this.vadEnabled = vadEnabled;
        this.vadThreshold = Math.max(0.0f, Math.min(1.0f, vadThreshold));
        this.sileroVadThreshold = Math.max(0.0f, Math.min(1.0f, sileroVadThreshold));
        this.translateToEnglish = translateToEnglish;
        final String promptValue = prompt == null ? DEFAULT_PROMPT : prompt.trim();
        this.prompt = promptValue.length() <= 300 ? promptValue : promptValue.substring(0, 300);
        this.fileTranscription = fileTranscription == null
                ? FileTranscriptionSettings.defaultSettings()
                : fileTranscription;
    }

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
                DEFAULT_NO_CONTEXT,
                DEFAULT_PRINT_TIMESTAMPS,
                DEFAULT_USE_GPU,
                DEFAULT_AUDIO_RECORDING_ENABLED,
                DEFAULT_AUTO_RETRANSCRIBE_ENABLED,
                DEFAULT_VAD_ENABLED,
                DEFAULT_VAD_THRESHOLD,
                DEFAULT_TRANSLATE_TO_ENGLISH,
                DEFAULT_PROMPT,
                DEFAULT_SILERO_VAD_THRESHOLD,
                FileTranscriptionSettings.defaultSettings()
        );
    }

    @NonNull
    @Contract("_ -> new")
    public WhisperSettings withModel(final WhisperModelOption model) {
        return new WhisperSettings(
                model,
                language,
                windowMs,
                overlapMs,
                minFinalMs,
                maxThreads,
                noContext,
                printTimestamps,
                useGpu,
                audioRecordingEnabled,
                autoRetranscribeEnabled,
                vadEnabled,
                vadThreshold,
                translateToEnglish,
                prompt,
                sileroVadThreshold,
                fileTranscription
        );
    }

    /**
     * ファイル一括設定だけを差し替えた設定を返します。
     * @param value 新しい一括設定。例: {@code FileTranscriptionSettings.defaultSettings()}
     * @return リアルタイム設定を維持した新しい設定。例: {@code WhisperSettings}
     */
    @NonNull
    public WhisperSettings withFileTranscription(
            @NonNull final FileTranscriptionSettings value
    ) {
        return new WhisperSettings(
                model, language, windowMs, overlapMs, minFinalMs, maxThreads, noContext,
                printTimestamps, useGpu, audioRecordingEnabled, autoRetranscribeEnabled,
                vadEnabled, vadThreshold, translateToEnglish, prompt, sileroVadThreshold, value
        );
    }

    /**
     * クイックスタートで変更できるリアルタイム設定を差し替えます。
     * @param newWindowMs 推論窓ms。例: {@code 5000}
     * @param newVadThreshold CTranslate2 no-speech閾値。例: {@code 0.6f}
     * @param recordingEnabled WAVを保存するならtrue。例: {@code true}
     * @param retranscribeEnabled 録音終了後にWhisper.cppで再推論するならtrue。例: {@code true}
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
                model, language, newWindowMs, overlapMs, minFinalMs, maxThreads, noContext,
                printTimestamps, useGpu, recordingEnabled, retranscribeEnabled,
                vadEnabled, newVadThreshold, translateToEnglish, prompt,
                sileroVadThreshold, fileTranscription
        );
    }

    public WhisperModelOption model() {
        return model;
    }

    public String language() {
        return language;
    }

    public int windowMs() {
        return windowMs;
    }

    public int overlapMs() {
        return overlapMs;
    }

    public int minFinalMs() {
        return minFinalMs;
    }

    public int maxThreads() {
        return maxThreads;
    }

    public boolean noContext() {
        return noContext;
    }

    public boolean printTimestamps() {
        return printTimestamps;
    }
    public boolean useGpu() {return useGpu;}
    public boolean audioRecordingEnabled() { return audioRecordingEnabled; }
    public boolean autoRetranscribeEnabled() { return autoRetranscribeEnabled; }
    public boolean vadEnabled() { return vadEnabled; }
    public float vadThreshold() { return vadThreshold; }
    public float sileroVadThreshold() { return sileroVadThreshold; }
    public boolean translateToEnglish() { return translateToEnglish; }
    public String prompt() { return prompt; }
    public FileTranscriptionSettings fileTranscription() { return fileTranscription; }

    private static String normalizeLanguage(final String value) {
        return WhisperLanguageOption.fromValue(value).value();
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
