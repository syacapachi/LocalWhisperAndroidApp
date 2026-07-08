package jp.ac.gifu_u.programmingjissen2.Record;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;

/** Whisper 用に変換済みの音声データです。 */
public record DecodedAudio(float[] samples, int sampleRate) {
    /**
     * 音声長をミリ秒で返します。
     *
     * @return 音声長。例: {@code 1000}
     */
    public long durationMs() {
        if (sampleRate <= 0) {
            return 0;
        }
        return samples.length * 1000L / sampleRate;
    }

    @NonNull
    @Override
    public String toString() {
        return StringBufferBuilderPool.Join(
                "",
                "DecodedAudio(samples=",
                samples.length,
                ", sampleRate=",
                sampleRate,
                ")"
        );
    }
}
