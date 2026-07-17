package jp.ac.gifu_u.programmingjissen2.Record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** RecordingAudioSourceのIntent復元値とキャプチャ要否を検証します。 */
public class RecordingAudioSourceTest {
    /** 引数例APP_CAPTUREを復元し、戻り値が同じenumになることを確認します。例外はありません。 */
    @Test
    public void fromNameRestoresCaptureSource() {
        assertEquals(
                RecordingAudioSource.APP_CAPTURE,
                RecordingAudioSource.fromName("APP_CAPTURE")
        );
    }

    /** 引数例nullではマイクを返すことを確認します。戻り値例MICROPHONE、例外はありません。 */
    @Test
    public void fromNameUsesMicrophoneForNull() {
        assertEquals(
                RecordingAudioSource.MICROPHONE,
                RecordingAudioSource.fromName(null)
        );
    }

    /** 各enumを引数相当として、戻り値例true/falseのキャプチャ要否を確認します。例外はありません。 */
    @Test
    public void captureRequirementMatchesSource() {
        assertFalse(RecordingAudioSource.MICROPHONE.requiresAppCapture());
        assertTrue(RecordingAudioSource.APP_CAPTURE.requiresAppCapture());
        assertTrue(RecordingAudioSource.MICROPHONE_AND_APP.requiresAppCapture());
    }
}
