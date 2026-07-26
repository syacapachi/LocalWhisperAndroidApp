package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jp.ac.gifu_u.programmingjissen2.Record.Buffer.DirectPcm16Builder;

/** デコード済み音声をWhisper用モノラルPCM16へ変換するutilityです。 */
public final class Pcm16AudioConverter {
    private Pcm16AudioConverter() {
    }

    /**
     * MediaCodecの出力PCMをモノラルPCM16に変換して末尾へ追加します。
     *
     * @param buffer MediaCodec の出力 ByteBuffer。例: {@code outputBuffer}
     * @param info 出力範囲を表す BufferInfo。例: {@code new MediaCodec.BufferInfo()}
     * @param channelCount 入力チャンネル数。例: {@code 2}
     * @param pcmEncoding PCM エンコード形式。例: {@code AudioFormat.ENCODING_PCM_16BIT}
     * @param output Directチャンクへの追加先。例: {@code new DirectPcm16Builder()}
     * @return 追加した mono サンプル数。例: {@code 16000}
     * @throws IllegalArgumentException 未対応の PCM 形式や不正なチャンネル数の場合
     */
    public static int appendMonoPcm16(
            @NonNull final ByteBuffer buffer,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding,
            @NonNull final DirectPcm16Builder output
    ) {
        if (channelCount <= 0) {
            throw new IllegalArgumentException("channelCount must be positive");
        }

        final ByteBuffer duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        duplicate.position(info.offset);
        duplicate.limit(info.offset + info.size);

        if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            return appendPcm16(duplicate, channelCount, output);
        }
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            return appendPcmFloat(duplicate, channelCount, output);
        }
        if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
            return appendPcm8(duplicate, channelCount, output);
        }

        throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
    }

    /**
     * リサンプリング後に必要な配列長を計算します。
     * @param sampleCount 入力数。例: {@code 44100}
     * @param sourceSampleRate 入力Hz。例: {@code 44100}
     * @param targetSampleRate 出力Hz。例: {@code 16000}
     * @return 必要長。例: {@code 16000}
     * @throws IllegalArgumentException 引数が負、またはレートが0以下の場合
     */
    public static int resampledLength(
            final int sampleCount,
            final int sourceSampleRate,
            final int targetSampleRate
    ) {
        if (sampleCount < 0 || sourceSampleRate <= 0 || targetSampleRate <= 0) {
            throw new IllegalArgumentException("sample count and rates must be valid");
        }
        return sampleCount == 0 ? 0 : Math.max(
                1,
                (int) Math.round(sampleCount * (double) targetSampleRate / sourceSampleRate)
        );
    }

    private static int appendPcm16(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final DirectPcm16Builder output
    ) {
        final int frameCount = buffer.remaining() / (Short.BYTES * channelCount);
        for (int frame = 0; frame < frameCount; frame++) {
            int mixed = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += buffer.getShort();
            }
            output.append(clampToShort(Math.round(mixed / (float) channelCount)));
        }
        return frameCount;
    }

    private static int appendPcmFloat(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final DirectPcm16Builder output
    ) {
        final int frameCount = buffer.remaining() / (Float.BYTES * channelCount);
        for (int frame = 0; frame < frameCount; frame++) {
            float mixed = 0.0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += buffer.getFloat();
            }
            output.append(floatToPcm16(mixed / channelCount));
        }
        return frameCount;
    }

    private static int appendPcm8(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final DirectPcm16Builder output
    ) {
        final int frameCount = buffer.remaining() / channelCount;
        for (int frame = 0; frame < frameCount; frame++) {
            int mixed = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += (buffer.get() & 0xff) - 128;
            }
            output.append(clampToShort(Math.round(mixed * 256.0f / channelCount)));
        }
        return frameCount;
    }

    /** @param value -1～1のPCM。例: {@code 0.5f} @return PCM16。例: {@code 16384} */
    private static short floatToPcm16(final float value) {
        final float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        return clampToShort(Math.round(clamped * (clamped < 0 ? 32768.0f : 32767.0f)));
    }

    /** @param value 整数PCM。例: {@code 40000} @return short範囲。例: {@code 32767} */
    private static short clampToShort(final int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

}
