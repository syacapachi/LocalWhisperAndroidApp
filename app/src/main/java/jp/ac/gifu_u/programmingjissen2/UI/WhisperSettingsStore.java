package jp.ac.gifu_u.programmingjissen2.UI;

import android.content.Context;
import android.content.SharedPreferences;

import Utils.StringPool.StringBufferBuilderPool;

/** Whisper 設定とモデル別推論時間統計の保存を担当します。 */
public class WhisperSettingsStore {
    private static final String PREF_NAME = "whisper_settings";

    private static final String KEY_MODEL = "model";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_WINDOW_MS = "window_ms";
    private static final String KEY_OVERLAP_MS = "overlap_ms";
    private static final String KEY_MIN_FINAL_MS = "min_final_ms";
    private static final String KEY_MAX_THREADS = "max_threads";
    private static final String KEY_NO_CONTEXT = "no_context";
    private static final String KEY_PRINT_TIMESTAMPS = "print_timestamps";

    private static final String STATS_COUNT = "stats_count";
    private static final String STATS_TOTAL_MS = "stats_total_ms";
    private static final String STATS_LAST_MS = "stats_last_ms";
    private static final String STATS_MIN_MS = "stats_min_ms";
    private static final String STATS_MAX_MS = "stats_max_ms";

    private final SharedPreferences preferences;

    public WhisperSettingsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
    }

    public WhisperSettings load() {
        return new WhisperSettings(
                WhisperModelOption.fromKey(preferences.getString(
                        KEY_MODEL,
                        WhisperSettings.DEFAULT_MODEL.key()
                )),
                preferences.getString(KEY_LANGUAGE, WhisperSettings.DEFAULT_LANGUAGE),
                preferences.getInt(KEY_WINDOW_MS, WhisperSettings.DEFAULT_WINDOW_MS),
                preferences.getInt(KEY_OVERLAP_MS, WhisperSettings.DEFAULT_OVERLAP_MS),
                preferences.getInt(KEY_MIN_FINAL_MS, WhisperSettings.DEFAULT_MIN_FINAL_MS),
                preferences.getInt(KEY_MAX_THREADS, WhisperSettings.DEFAULT_MAX_THREADS),
                preferences.getBoolean(KEY_NO_CONTEXT, WhisperSettings.DEFAULT_NO_CONTEXT),
                preferences.getBoolean(
                        KEY_PRINT_TIMESTAMPS,
                        WhisperSettings.DEFAULT_PRINT_TIMESTAMPS
                )
        );
    }

    public void save(WhisperSettings settings) {
        WhisperSettings value = settings == null ? WhisperSettings.defaultSettings() : settings;
        preferences.edit()
                .putString(KEY_MODEL, value.model().key())
                .putString(KEY_LANGUAGE, value.language())
                .putInt(KEY_WINDOW_MS, value.windowMs())
                .putInt(KEY_OVERLAP_MS, value.overlapMs())
                .putInt(KEY_MIN_FINAL_MS, value.minFinalMs())
                .putInt(KEY_MAX_THREADS, value.maxThreads())
                .putBoolean(KEY_NO_CONTEXT, value.noContext())
                .putBoolean(KEY_PRINT_TIMESTAMPS, value.printTimestamps())
                .apply();
    }

    public void saveModel(WhisperModelOption model) {
        save(load().withModel(model));
    }

    public WhisperInferenceStats loadStats(WhisperModelOption model) {
        String key = model == null ? WhisperSettings.DEFAULT_MODEL.key() : model.key();
        long count = preferences.getLong(statsKey(key, STATS_COUNT), 0);
        long minMs = preferences.getLong(statsKey(key, STATS_MIN_MS), 0);
        return new WhisperInferenceStats(
                key,
                count,
                preferences.getLong(statsKey(key, STATS_TOTAL_MS), 0),
                preferences.getLong(statsKey(key, STATS_LAST_MS), 0),
                count <= 0 ? 0 : minMs,
                preferences.getLong(statsKey(key, STATS_MAX_MS), 0)
        );
    }

    public void recordInference(String modelKey, long processingTimeMs) {
        if (modelKey == null || modelKey.isEmpty() || processingTimeMs < 0) {
            return;
        }

        WhisperModelOption model = WhisperModelOption.fromKey(modelKey);
        WhisperInferenceStats stats = loadStats(model);
        long count = stats.count() + 1;
        long totalMs = stats.totalMs() + processingTimeMs;
        long minMs = stats.hasSamples()
                ? Math.min(stats.minMs(), processingTimeMs)
                : processingTimeMs;
        long maxMs = Math.max(stats.maxMs(), processingTimeMs);

        preferences.edit()
                .putLong(statsKey(model.key(), STATS_COUNT), count)
                .putLong(statsKey(model.key(), STATS_TOTAL_MS), totalMs)
                .putLong(statsKey(model.key(), STATS_LAST_MS), processingTimeMs)
                .putLong(statsKey(model.key(), STATS_MIN_MS), minMs)
                .putLong(statsKey(model.key(), STATS_MAX_MS), maxMs)
                .apply();
    }

    public void resetStats() {
        SharedPreferences.Editor editor = preferences.edit();
        for (WhisperModelOption model : WhisperModelOption.values()) {
            editor.remove(statsKey(model.key(), STATS_COUNT));
            editor.remove(statsKey(model.key(), STATS_TOTAL_MS));
            editor.remove(statsKey(model.key(), STATS_LAST_MS));
            editor.remove(statsKey(model.key(), STATS_MIN_MS));
            editor.remove(statsKey(model.key(), STATS_MAX_MS));
        }
        editor.apply();
    }

    private String statsKey(String modelKey, String suffix) {
        return StringBufferBuilderPool.Join("_", modelKey, suffix);
    }
}
