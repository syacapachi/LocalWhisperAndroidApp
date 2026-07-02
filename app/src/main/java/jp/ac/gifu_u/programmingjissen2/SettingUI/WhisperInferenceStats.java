package jp.ac.gifu_u.programmingjissen2.SettingUI;

/** Whisper モデルごとの推論時間統計です。 */
public final class WhisperInferenceStats {
    private final String modelKey;
    private final long count;
    private final long totalMs;
    private final long lastMs;
    private final long minMs;
    private final long maxMs;

    public WhisperInferenceStats(
            String modelKey,
            long count,
            long totalMs,
            long lastMs,
            long minMs,
            long maxMs
    ) {
        this.modelKey = modelKey;
        this.count = Math.max(0, count);
        this.totalMs = Math.max(0, totalMs);
        this.lastMs = Math.max(0, lastMs);
        this.minMs = Math.max(0, minMs);
        this.maxMs = Math.max(0, maxMs);
    }

    public String modelKey() {
        return modelKey;
    }

    public long count() {
        return count;
    }

    public long totalMs() {
        return totalMs;
    }

    public long lastMs() {
        return lastMs;
    }

    public long minMs() {
        return minMs;
    }

    public long maxMs() {
        return maxMs;
    }

    public long averageMs() {
        if (count <= 0) {
            return 0;
        }
        return totalMs / count;
    }

    public boolean hasSamples() {
        return count > 0;
    }
}
