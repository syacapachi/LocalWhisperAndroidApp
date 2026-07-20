package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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
     * @param output 変換結果の追加先。例: {@code new ShortArrayBuilder()}
     * @return 追加した mono サンプル数。例: {@code 16000}
     * @throws IllegalArgumentException 未対応の PCM 形式や不正なチャンネル数の場合
     */
    public static int appendMonoPcm16(
            @NonNull final ByteBuffer buffer,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding,
            @NonNull final ShortArrayBuilder output
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
     * モノラルPCM16を指定サンプリングレートへリサンプリングします。
     *
     * @param samples モノラルPCM16。例: {@code new short[]{0, 16384}}
     * @param sourceSampleRate 入力サンプリングレート。例: {@code 44100}
     * @param targetSampleRate 出力サンプリングレート。例: {@code 16000}
     * @return リサンプリング済みPCM16。例: {@code new short[]{0}}
     * @throws IllegalArgumentException サンプリングレートが 0 以下の場合
     */
    @NonNull
    public static short[] resample(
            @NonNull final short[] samples,
            final int sourceSampleRate,
            final int targetSampleRate
    ) {
        final short[] output = new short[resampledLength(
                samples.length, sourceSampleRate, targetSampleRate)];
        resampleTo(samples, samples.length, sourceSampleRate, targetSampleRate, output);
        return output;
    }

    /**
     * PCM16を呼び出し側が再利用できる出力配列へリサンプリングします。
     * @param samples 入力PCM16。例: {@code new short[]{0, 16384}}
     * @param sampleCount 有効入力数。例: {@code 2}
     * @param sourceSampleRate 入力Hz。例: {@code 44100}
     * @param targetSampleRate 出力Hz。例: {@code 16000}
     * @param output 出力先。例: {@code new short[1]}
     * @return 書き込んだサンプル数。例: {@code 1}
     * @throws IllegalArgumentException サンプル数、レート、出力容量が不正な場合
     */
    public static int resampleTo(
            @NonNull final short[] samples,
            final int sampleCount,
            final int sourceSampleRate,
            final int targetSampleRate,
            @NonNull final short[] output
    ) {
        if (sourceSampleRate <= 0 || targetSampleRate <= 0) {
            throw new IllegalArgumentException("sample rate must be positive");
        }
        if (sampleCount < 0 || sampleCount > samples.length) {
            throw new IllegalArgumentException("sampleCount is outside samples");
        }
        final int outputLength = resampledLength(
                sampleCount, sourceSampleRate, targetSampleRate);
        if (output.length < outputLength) {
            throw new IllegalArgumentException("output is too small");
        }
        if (sampleCount == 0) {
            return 0;
        }
        if (sourceSampleRate == targetSampleRate) {
            System.arraycopy(samples, 0, output, 0, sampleCount);
            return sampleCount;
        }
        final double scale = (double) sourceSampleRate / targetSampleRate;

        for (int i = 0; i < outputLength; i++) {
            final double sourceIndex = i * scale;
            final int left = Math.min((int) sourceIndex, sampleCount - 1);
            final int right = Math.min(left + 1, sampleCount - 1);
            final double fraction = sourceIndex - left;
            output[i] = clampToShort((int) Math.round(
                    samples[left] + (samples[right] - samples[left]) * fraction));
        }
        return outputLength;
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
            @NonNull final ShortArrayBuilder output
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
            @NonNull final ShortArrayBuilder output
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
            @NonNull final ShortArrayBuilder output
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

    /** short配列へPCMサンプルを追加する簡易builderです。 */
    public static final class ShortArrayBuilder {
        private short[] buffer = new short[16_000];
        private int size;

        /**
         * PCM16サンプルを1つ追加します。
         *
         * @param value 追加するサンプル。例: {@code (short) 8192}
         * @return 追加後のサンプル数。例: {@code 16001}
         */
        public int append(final short value) {
            ensureCapacity(size + 1);
            buffer[size++] = value;
            return size;
        }

        /**
         * 現在の内容を配列として返します。
         *
         * @return 追加済みサンプルだけを持つ配列。例: {@code new short[]{8192}}
         */
        @NonNull
        public short[] toArray() {
            return Arrays.copyOf(buffer, size);
        }

        /** @return 現在の有効サンプル数。例: {@code 16000}。例外はありません。 */
        public int size() {
            return size;
        }

        /**
         * builder内部配列から直接、呼び出し側の配列へリサンプリングします。
         * @param sourceSampleRate 入力Hz。例: {@code 44100}
         * @param targetSampleRate 出力Hz。例: {@code 16000}
         * @param output 出力先。例: {@code new short[16000]}
         * @return 書き込んだ数。例: {@code 16000}
         * @throws IllegalArgumentException レートまたは出力容量が不正な場合
         */
        public int resampleTo(
                final int sourceSampleRate,
                final int targetSampleRate,
                @NonNull final short[] output
        ) {
            return Pcm16AudioConverter.resampleTo(
                    buffer, size, sourceSampleRate, targetSampleRate, output);
        }

        private void ensureCapacity(final int capacity) {
            if (capacity <= buffer.length) {
                return;
            }

            int newCapacity = buffer.length;
            while (newCapacity < capacity) {
                newCapacity *= 2;
            }
            buffer = Arrays.copyOf(buffer, newCapacity);
        }
    }
}
