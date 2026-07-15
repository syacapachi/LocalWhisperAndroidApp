package jp.ac.gifu_u.programmingjissen2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;

/** 音声保存と自動再推論設定の依存関係を検証します。 */
public class WhisperSettingsTest {
    /** 既定ではプライバシーを優先し、音声保存と自動再推論がOFFであることを確認します。 */
    @Test
    public void defaultSettingsDisableAudioRecording() {
        final WhisperSettings settings = WhisperSettings.defaultSettings();
        assertFalse(settings.audioRecordingEnabled());
        assertFalse(settings.autoRetranscribeEnabled());
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
        assertTrue(settings.withModel(WhisperModelOption.SMALL).autoRetranscribeEnabled());
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
