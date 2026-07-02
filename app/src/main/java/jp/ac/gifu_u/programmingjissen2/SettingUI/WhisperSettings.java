package jp.ac.gifu_u.programmingjissen2.SettingUI;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

/** Whisper 推論に関係するユーザー設定です。 */
public final class WhisperSettings {
    public static final WhisperModelOption DEFAULT_MODEL = WhisperModelOption.BASE;
    public static final String DEFAULT_LANGUAGE = WhisperLanguageOption.JAPANESE.value();
    public static final int DEFAULT_WINDOW_MS = 5000;
    public static final int DEFAULT_OVERLAP_MS = 1000;
    public static final int DEFAULT_MIN_FINAL_MS = 1000;
    public static final int DEFAULT_MAX_THREADS = 4;
    public static final boolean DEFAULT_NO_CONTEXT = true;
    public static final boolean DEFAULT_PRINT_TIMESTAMPS = false;
    public static final boolean DEFAULT_USE_GPU = false;
    private final WhisperModelOption model;
    private final String language;
    private final int windowMs;
    private final int overlapMs;
    private final int minFinalMs;
    private final int maxThreads;
    private final boolean noContext;
    private final boolean printTimestamps;
    private final boolean useGpu;

    public WhisperSettings(
            final WhisperModelOption model,
            final String language,
            final int windowMs,
            final int overlapMs,
            final int minFinalMs,
            final int maxThreads,
            final boolean noContext,
            final boolean printTimestamps,
            final boolean useGpu
    ) {
        this.model = model == null ? DEFAULT_MODEL : model;
        this.language = normalizeLanguage(language);
        this.windowMs = clamp(windowMs, 1000, 30000);
        this.overlapMs = clamp(overlapMs, 0, Math.max(0, this.windowMs - 250));
        this.minFinalMs = clamp(minFinalMs, 250, this.windowMs);
        this.maxThreads = clamp(maxThreads, 1, 8);
        this.noContext = noContext;
        this.printTimestamps = printTimestamps;
        this.useGpu = useGpu;
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
                DEFAULT_USE_GPU
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
                useGpu
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

    private static String normalizeLanguage(final String value) {
        return WhisperLanguageOption.fromValue(value).value();
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}
