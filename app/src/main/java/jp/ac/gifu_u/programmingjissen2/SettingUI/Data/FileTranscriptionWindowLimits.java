package jp.ac.gifu_u.programmingjissen2.SettingUI.Data;

/** ファイル文字起こしの推論窓を端末メモリに応じて制限するutilityです。 */
public final class FileTranscriptionWindowLimits {
    public static final int MIN_SECONDS = 30;
    public static final int ABSOLUTE_MAX_SECONDS = 5 * 60;
    public static final int STEP_SECONDS = 10;

    private static final long DECODE_SAMPLE_RATE_ESTIMATE = 48_000L;
    private static final long WHISPER_SAMPLE_RATE = 16_000L;
    private static final long PEAK_BYTES_PER_SECOND =
            DECODE_SAMPLE_RATE_ESTIMATE * Short.BYTES
                    + WHISPER_SAMPLE_RATE
                    * (2L * Short.BYTES + Float.BYTES + Float.BYTES);

    private FileTranscriptionWindowLimits() {
    }

    /**
     * 現在のアプリ最大メモリから推論窓の上限を計算します。
     * @return 10秒単位の上限秒数。例: {@code 300}
     * 例外はなく、メモリが極端に少ない場合も30秒を返します。
     */
    public static int maxSeconds() {
        return maxSeconds(Runtime.getRuntime().maxMemory());
    }

    /**
     * 指定メモリの半分にPCM16・JNI float・VAD floatが収まる上限を計算します。
     * @param appMaxMemoryBytes アプリが利用できる最大byte数。例: {@code 256L * 1024 * 1024}
     * @return 30～300秒かつ10秒単位の上限。例: {@code 300}
     * 例外はなく、0以下の場合は最小値を返します。
     */
    public static int maxSeconds(final long appMaxMemoryBytes) {
        final long usableBytes = Math.max(0L, appMaxMemoryBytes) / 2L;
        final long secondsByMemory =
                usableBytes / PEAK_BYTES_PER_SECOND;
        final long capped = Math.min(ABSOLUTE_MAX_SECONDS, secondsByMemory);
        final long stepped = capped / STEP_SECONDS * STEP_SECONDS;
        return (int) Math.max(MIN_SECONDS, stepped);
    }

    /**
     * 秒数を端末上限内の10秒単位へ丸めます。
     * @param seconds 入力秒数。例: {@code 67}
     * @param maxSeconds 端末上限秒数。例: {@code 300}
     * @return 補正後秒数。例: {@code 70}
     * 例外はなく、上限が30秒未満でも30秒を返します。
     */
    public static int normalizeSeconds(final int seconds, final int maxSeconds) {
        final int safeMax = Math.max(MIN_SECONDS, maxSeconds);
        final int rounded = Math.round(seconds / (float) STEP_SECONDS) * STEP_SECONDS;
        return Math.max(MIN_SECONDS, Math.min(safeMax, rounded));
    }
}
