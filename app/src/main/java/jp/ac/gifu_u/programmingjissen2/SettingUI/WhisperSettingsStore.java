package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperCppModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** Whisper 設定とモデル別推論時間統計の保存を担当します。 */
public final class WhisperSettingsStore {
    private static final String PREF_NAME = "whisper_settings";

    private static final String KEY_MODEL = "model";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_WINDOW_MS = "window_ms";
    private static final String KEY_OVERLAP_MS = "overlap_ms";
    private static final String KEY_MIN_FINAL_MS = "min_final_ms";
    private static final String KEY_MAX_THREADS = "max_threads";
    private static final String KEY_AUDIO_RECORDING_ENABLED = "audio_recording_enabled";
    private static final String KEY_AUTO_RETRANSCRIBE_ENABLED = "auto_retranscribe_enabled";
    private static final String KEY_VAD_ENABLED = "vad_enabled";
    private static final String KEY_VAD_THRESHOLD = "vad_threshold";
    private static final String KEY_TRANSLATE_TO_ENGLISH = "translate_to_english";
    private static final String KEY_PROMPT = "prompt";
    private static final String KEY_FILE_MODEL = "file_model";
    private static final String KEY_FILE_LANGUAGE = "file_language";
    private static final String KEY_FILE_MAX_THREADS = "file_max_threads";
    private static final String KEY_FILE_WINDOW_MS = "file_window_ms";
    private static final String KEY_FILE_VAD_ENABLED = "file_vad_enabled";
    private static final String KEY_FILE_VAD_THRESHOLD = "file_vad_threshold";
    private static final String KEY_FILE_TRANSLATE_TO_ENGLISH = "file_translate_to_english";
    private static final String KEY_FILE_PROMPT = "file_prompt";
    private static final String STATS_COUNT = "stats_count";
    private static final String STATS_TOTAL_MS = "stats_total_ms";
    private static final String STATS_LAST_MS = "stats_last_ms";
    private static final String STATS_MIN_MS = "stats_min_ms";
    private static final String STATS_MAX_MS = "stats_max_ms";

    private final SharedPreferences preferences;
    private final ExternalModelRepository modelRepository;

    /**
     * Contentに記されたSharedPreferencesから設定値を読み込みます。
     * @param context アプリのContext
     */
    public WhisperSettingsStore(@NonNull final Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
        modelRepository = new ExternalModelRepository(context);
    }

    @NonNull
    @Contract(" -> new")
    public WhisperSettings load() {
        final ITranscriptionModel savedRealtime = modelRepository.find(
                preferences.getString(KEY_MODEL, WhisperSettings.DEFAULT_MODEL.key()),
                WhisperInferenceEngine.CTRANSLATE2);
        final ITranscriptionModel realtimeModel = savedRealtime == null
                ? WhisperSettings.DEFAULT_MODEL : savedRealtime;
        final FileTranscriptionSettings fileSettings = loadFileSettings();
        return new WhisperSettings(
                realtimeModel,
                preferences.getString(KEY_LANGUAGE, WhisperSettings.DEFAULT_LANGUAGE),
                preferences.getInt(KEY_WINDOW_MS, WhisperSettings.DEFAULT_WINDOW_MS),
                preferences.getInt(KEY_OVERLAP_MS, WhisperSettings.DEFAULT_OVERLAP_MS),
                preferences.getInt(KEY_MIN_FINAL_MS, WhisperSettings.DEFAULT_MIN_FINAL_MS),
                preferences.getInt(KEY_MAX_THREADS, WhisperSettings.DEFAULT_MAX_THREADS),
                preferences.getBoolean(KEY_AUDIO_RECORDING_ENABLED,
                        WhisperSettings.DEFAULT_AUDIO_RECORDING_ENABLED),
                preferences.getBoolean(KEY_AUTO_RETRANSCRIBE_ENABLED,
                        WhisperSettings.DEFAULT_AUTO_RETRANSCRIBE_ENABLED),
                preferences.getBoolean(KEY_VAD_ENABLED, WhisperSettings.DEFAULT_VAD_ENABLED),
                preferences.getFloat(KEY_VAD_THRESHOLD, WhisperSettings.DEFAULT_VAD_THRESHOLD),
                preferences.getBoolean(KEY_TRANSLATE_TO_ENGLISH,
                        WhisperSettings.DEFAULT_TRANSLATE_TO_ENGLISH),
                preferences.getString(KEY_PROMPT, WhisperSettings.DEFAULT_PROMPT),
                fileSettings
        );
    }

    /**
     * ファイル一括設定を読み込みます。
     * @return 独立したWhisper.cpp設定。例: {@code FileTranscriptionSettings}
     */
    @NonNull
    private FileTranscriptionSettings loadFileSettings() {
        final ITranscriptionModel savedModel = modelRepository.find(
                preferences.getString(
                        KEY_FILE_MODEL,
                        FileTranscriptionSettings.DEFAULT_MODEL.key()
                ),
                WhisperInferenceEngine.WHISPER_CPP);
        return new FileTranscriptionSettings(
                savedModel == null ? FileTranscriptionSettings.DEFAULT_MODEL : savedModel,
                preferences.getString(KEY_FILE_LANGUAGE, WhisperSettings.DEFAULT_LANGUAGE),
                preferences.getInt(KEY_FILE_MAX_THREADS, WhisperSettings.DEFAULT_MAX_THREADS),
                preferences.getInt(
                        KEY_FILE_WINDOW_MS,
                        FileTranscriptionSettings.DEFAULT_WINDOW_MS),
                preferences.getBoolean(
                        KEY_FILE_VAD_ENABLED,
                        FileTranscriptionSettings.DEFAULT_VAD_ENABLED),
                preferences.getFloat(
                        KEY_FILE_VAD_THRESHOLD,
                        FileTranscriptionSettings.DEFAULT_VAD_THRESHOLD
                ),
                preferences.getBoolean(
                        KEY_FILE_TRANSLATE_TO_ENGLISH,
                        WhisperSettings.DEFAULT_TRANSLATE_TO_ENGLISH
                ),
                preferences.getString(
                        KEY_FILE_PROMPT,
                        FileTranscriptionSettings.DEFAULT_PROMPT
                )
        );
    }

    public void save(final WhisperSettings settings) {
        final WhisperSettings value = settings == null ? WhisperSettings.defaultSettings() : settings;
        final FileTranscriptionSettings file = value.fileTranscription();
        preferences.edit()
                .putString(KEY_MODEL, value.model().key())
                .putString(KEY_LANGUAGE, value.language())
                .putInt(KEY_WINDOW_MS, value.windowMs())
                .putInt(KEY_OVERLAP_MS, value.overlapMs())
                .putInt(KEY_MIN_FINAL_MS, value.minFinalMs())
                .putInt(KEY_MAX_THREADS, value.maxThreads())
                .putBoolean(KEY_AUDIO_RECORDING_ENABLED, value.audioRecordingEnabled())
                .putBoolean(KEY_AUTO_RETRANSCRIBE_ENABLED, value.autoRetranscribeEnabled())
                .putBoolean(KEY_VAD_ENABLED, value.vadEnabled())
                .putFloat(KEY_VAD_THRESHOLD, value.vadThreshold())
                .putBoolean(KEY_TRANSLATE_TO_ENGLISH, value.translateToEnglish())
                .putString(KEY_PROMPT, value.prompt())
                .putString(KEY_FILE_MODEL, file.model().key())
                .putString(KEY_FILE_LANGUAGE, file.language())
                .putInt(KEY_FILE_MAX_THREADS, file.maxThreads())
                .putInt(KEY_FILE_WINDOW_MS, file.windowMs())
                .putBoolean(KEY_FILE_VAD_ENABLED, file.vadEnabled())
                .putFloat(KEY_FILE_VAD_THRESHOLD, file.vadThreshold())
                .putBoolean(KEY_FILE_TRANSLATE_TO_ENGLISH, file.translateToEnglish())
                .putString(KEY_FILE_PROMPT, file.prompt())
                .apply();
    }

    public void saveModel(final ITranscriptionModel model) {
        save(load().withModel(model));
    }

    @NonNull
    public WhisperInferenceStats loadStats(final ITranscriptionModel model) {
        final String key = model == null ? WhisperSettings.DEFAULT_MODEL.key() : model.key();
        return loadStats(key);
    }

    /**
     * モデルキーに対応する統計を読み込みます。
     * @param key モデルキー。例: {@code "cpp-small-q8-0"}
     * @return 保存済み統計。例: {@code WhisperInferenceStats}
     */
    @NonNull
    private WhisperInferenceStats loadStats(@NonNull final String key) {
        final long count = preferences.getLong(statsKey(key, STATS_COUNT), 0);
        final long minMs = preferences.getLong(statsKey(key, STATS_MIN_MS), 0);
        return new WhisperInferenceStats(
                key,
                count,
                preferences.getLong(statsKey(key, STATS_TOTAL_MS), 0),
                preferences.getLong(statsKey(key, STATS_LAST_MS), 0),
                count <= 0 ? 0 : minMs,
                preferences.getLong(statsKey(key, STATS_MAX_MS), 0)
        );
    }

    public void recordInference(final String modelKey, final long processingTimeMs) {
        if (modelKey == null || modelKey.isEmpty() || processingTimeMs < 0) {
            return;
        }
        final String key = knownModelKey(modelKey);
        if (key == null) {
            return;
        }
        final WhisperInferenceStats stats = loadStats(key);
        final long count = stats.count() + 1;
        final long totalMs = stats.totalMs() + processingTimeMs;
        final long minMs = stats.hasSamples()
                ? Math.min(stats.minMs(), processingTimeMs)
                : processingTimeMs;
        final long maxMs = Math.max(stats.maxMs(), processingTimeMs);

        preferences.edit()
                .putLong(statsKey(key, STATS_COUNT), count)
                .putLong(statsKey(key, STATS_TOTAL_MS), totalMs)
                .putLong(statsKey(key, STATS_LAST_MS), processingTimeMs)
                .putLong(statsKey(key, STATS_MIN_MS), minMs)
                .putLong(statsKey(key, STATS_MAX_MS), maxMs)
                .apply();
    }

    /**
     * 統計保存を許可する既知のモデルキーを返します。
     * @param candidate 検査値。例: {@code "cpp-small-q8-0"}
     * @return 一致したキー。不明値はnull。例: {@code "cpp-small-q8-0"}
     */
    private String knownModelKey(final String candidate) {
        for (WhisperModelOption model : WhisperModelOption.values()) {
            if (model.key().equals(candidate)) {
                return candidate;
            }
        }
        for (WhisperCppModelOption model : WhisperCppModelOption.values()) {
            if (model.key().equals(candidate)) {
                return candidate;
            }
        }
        return candidate.startsWith("external-") ? candidate : null;
    }

    public void resetStats() {
        final SharedPreferences.Editor editor = preferences.edit();
        for (WhisperInferenceEngine engine : WhisperInferenceEngine.values()) {
            for (ITranscriptionModel model : modelRepository.list(engine)) {
                editor.remove(statsKey(model.key(), STATS_COUNT));
                editor.remove(statsKey(model.key(), STATS_TOTAL_MS));
                editor.remove(statsKey(model.key(), STATS_LAST_MS));
                editor.remove(statsKey(model.key(), STATS_MIN_MS));
                editor.remove(statsKey(model.key(), STATS_MAX_MS));
            }
        }
        editor.apply();
    }

    @NonNull
    private String statsKey(final String modelKey, final String suffix) {
        return StringBufferBuilderPool.Join("_", modelKey, suffix);
    }
}
