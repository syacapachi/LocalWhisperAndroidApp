package jp.ac.gifu_u.programmingjissen2.Record;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import Utils.StringPool.StringBufferBuilderPool;

/** Whisper 用に変換済みの音声データです。 */
public record DecodedAudio(ByteBuffer samples, int sampleCount, int sampleRate) {
    /**
     * Direct PCM16音声を作成します。
     * @param samples Direct保存領域。例: {@code ByteBuffer.allocateDirect(32000)}
     * @param sampleCount 有効サンプル数。例: {@code 16000}
     * @param sampleRate Hz。例: {@code 16000}
     * @throws IllegalArgumentException 非Direct、負数、容量超過、または不正レートの場合
     */
    public DecodedAudio {
        if (samples == null || !samples.isDirect()) {
            throw new IllegalArgumentException("samples must be a DirectByteBuffer");
        }
        if (sampleCount < 0 || (long) sampleCount * Short.BYTES > samples.capacity()) {
            throw new IllegalArgumentException("sampleCount is outside samples");
        }
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive");
        }
    }

    /**
     * 音声長をミリ秒で返します。
     *
     * @return 音声長。例: {@code 1000}
     */
    public long durationMs() {
        return sampleCount * 1000L / sampleRate;
    }

    @NonNull
    @Override
    public String toString() {
        return StringBufferBuilderPool.Join(
                "",
                "DecodedAudio(samples=",
                sampleCount,
                ", sampleRate=",
                sampleRate,
                ")"
        );
    }
}
