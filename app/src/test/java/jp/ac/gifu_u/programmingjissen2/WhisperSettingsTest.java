package jp.ac.gifu_u.programmingjissen2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** 音声保存と自動再推論設定の依存関係を検証します。 */
public class WhisperSettingsTest {
    /** 既定ではプライバシーを優先し、音声保存と自動再推論がOFFであることを確認します。 */
    @Test
    public void defaultSettingsDisableAudioRecording() {
        final WhisperSettings settings = WhisperSettings.defaultSettings();
        assertFalse(settings.audioRecordingEnabled());
        assertFalse(settings.autoRetranscribeEnabled());
        assertTrue(settings.vadEnabled());
        assertEquals(0.5f, settings.sileroVadThreshold(), 0.0f);
    }

    /** 音声保存OFFなら自動再推論指定が強制的にOFFになることを確認します。 */
    @Test
    public void autoRetranscriptionRequiresAudioRecording() {
        final WhisperSettings settings = createSettings(false, true);
        assertFalse(settings.audioRecordingEnabled());
        assertFalse(settings.autoRetranscribeEnabled());
    }

    /** 音声保存ONなら自動再推論を有効化できることを確認します。 */
    @Test
    public void autoRetranscriptionCanBeEnabledWithRecording() {
        final WhisperSettings settings = createSettings(true, true);
        assertTrue(settings.audioRecordingEnabled());
        assertTrue(settings.autoRetranscribeEnabled());
        assertTrue(settings.withModel(WhisperModelOption.CT2_MEDIUM_INT8)
                .autoRetranscribeEnabled());
    }

    /** モデル選択を引数例CT2_BASE_INT8へ変えると、戻り値の推論系がCTranslate2になることを確認します。 */
    @Test
    public void modelSelectsInferenceEngine() {
        final WhisperSettings settings = createSettings(false, false)
                .withModel(WhisperModelOption.CT2_BASE_INT8);
        assertEquals(WhisperInferenceEngine.CTRANSLATE2, settings.model().engine());
        assertEquals("int8", settings.model().computeType());
    }

    /** medium量子化モデルのassetsパスとCTranslate2推論系を確認します。例外はありません。 */
    @Test
    public void mediumInt8ModelIsSelectable() {
        assertEquals("ctranslate2/openai-whisper-medium-int8",
                WhisperModelOption.CT2_MEDIUM_INT8.assetName());
        assertEquals(WhisperInferenceEngine.CTRANSLATE2,
                WhisperModelOption.CT2_MEDIUM_INT8.engine());
        assertEquals("ggml-small_q8_0.bin",
                WhisperModelOption.CT2_MEDIUM_INT8.whisperCppAssetName());
    }

    /** Silero VAD閾値の引数例{@code 1.4f}が上限へ補正されることを確認します。 */
    @Test
    public void sileroVadThresholdIsClamped() {
        final WhisperSettings defaults = WhisperSettings.defaultSettings();
        final WhisperSettings settings = new WhisperSettings(
                defaults.model(), defaults.language(), defaults.windowMs(), defaults.overlapMs(),
                defaults.minFinalMs(), defaults.maxThreads(), defaults.noContext(),
                defaults.printTimestamps(), defaults.useGpu(),
                defaults.audioRecordingEnabled(), defaults.autoRetranscribeEnabled(),
                defaults.vadEnabled(), defaults.vadThreshold(), defaults.translateToEnglish(),
                defaults.prompt(), 1.4f
        );
        assertEquals(1.0f, settings.sileroVadThreshold(), 0.0f);
    }

    /**
     * テスト対象の設定を作成します。
     * @param recording 音声保存。例: {@code true}
     * @param retranscribe 自動再推論。例: {@code true}
     * @return 設定。例: {@code WhisperSettings}
     */
    private WhisperSettings createSettings(final boolean recording, final boolean retranscribe) {
        return new WhisperSettings(
                WhisperSettings.DEFAULT_MODEL,
                WhisperSettings.DEFAULT_LANGUAGE,
                WhisperSettings.DEFAULT_WINDOW_MS,
                WhisperSettings.DEFAULT_OVERLAP_MS,
                WhisperSettings.DEFAULT_MIN_FINAL_MS,
                WhisperSettings.DEFAULT_MAX_THREADS,
                WhisperSettings.DEFAULT_NO_CONTEXT,
                WhisperSettings.DEFAULT_PRINT_TIMESTAMPS,
                WhisperSettings.DEFAULT_USE_GPU,
                recording,
                retranscribe
        );
    }
}
