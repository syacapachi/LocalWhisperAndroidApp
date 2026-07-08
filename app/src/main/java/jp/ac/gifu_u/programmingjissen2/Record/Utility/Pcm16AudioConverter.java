package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** 16bit PCM などのデコード済み音声を Whisper 用 float PCM へ変換する utility です。 */
public final class Pcm16AudioConverter {
    private Pcm16AudioConverter() {
    }

    /**
     * MediaCodec の出力 PCM を mono float PCM に変換して末尾へ追加します。
     *
     * @param buffer MediaCodec の出力 ByteBuffer。例: {@code outputBuffer}
     * @param info 出力範囲を表す BufferInfo。例: {@code new MediaCodec.BufferInfo()}
     * @param channelCount 入力チャンネル数。例: {@code 2}
     * @param pcmEncoding PCM エンコード形式。例: {@code AudioFormat.ENCODING_PCM_16BIT}
     * @param output 変換結果の追加先。例: {@code new FloatArrayBuilder()}
     * @return 追加した mono サンプル数。例: {@code 16000}
     * @throws IllegalArgumentException 未対応の PCM 形式や不正なチャンネル数の場合
     */
    public static int appendMonoFloat(
            @NonNull final ByteBuffer buffer,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding,
            @NonNull final FloatArrayBuilder output
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
     * mono float PCM を指定サンプリングレートへリサンプリングします。
     *
     * @param samples mono float PCM。例: {@code new float[]{0.0f, 0.5f}}
     * @param sourceSampleRate 入力サンプリングレート。例: {@code 44100}
     * @param targetSampleRate 出力サンプリングレート。例: {@code 16000}
     * @return リサンプリング済み mono float PCM。例: {@code new float[]{0.0f}}
     * @throws IllegalArgumentException サンプリングレートが 0 以下の場合
     */
    @NonNull
    public static float[] resample(
            @NonNull final float[] samples,
            final int sourceSampleRate,
            final int targetSampleRate
    ) {
        if (sourceSampleRate <= 0 || targetSampleRate <= 0) {
            throw new IllegalArgumentException("sample rate must be positive");
        }
        if (samples.length == 0 || sourceSampleRate == targetSampleRate) {
            return Arrays.copyOf(samples, samples.length);
        }

        final int outputLength = Math.max(
                1,
                (int) Math.round(samples.length * (double) targetSampleRate / sourceSampleRate)
        );
        final float[] output = new float[outputLength];
        final double scale = (double) sourceSampleRate / targetSampleRate;

        for (int i = 0; i < outputLength; i++) {
            final double sourceIndex = i * scale;
            final int left = Math.min((int) sourceIndex, samples.length - 1);
            final int right = Math.min(left + 1, samples.length - 1);
            final float fraction = (float) (sourceIndex - left);
            output[i] = samples[left] + (samples[right] - samples[left]) * fraction;
        }
        return output;
    }

    private static int appendPcm16(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final FloatArrayBuilder output
    ) {
        final int frameCount = buffer.remaining() / (Short.BYTES * channelCount);
        for (int frame = 0; frame < frameCount; frame++) {
            int mixed = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += buffer.getShort();
            }
            output.append(clamp(mixed / (channelCount * 32768.0f)));
        }
        return frameCount;
    }

    private static int appendPcmFloat(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final FloatArrayBuilder output
    ) {
        final int frameCount = buffer.remaining() / (Float.BYTES * channelCount);
        for (int frame = 0; frame < frameCount; frame++) {
            float mixed = 0.0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += buffer.getFloat();
            }
            output.append(clamp(mixed / channelCount));
        }
        return frameCount;
    }

    private static int appendPcm8(
            @NonNull final ByteBuffer buffer,
            final int channelCount,
            @NonNull final FloatArrayBuilder output
    ) {
        final int frameCount = buffer.remaining() / channelCount;
        for (int frame = 0; frame < frameCount; frame++) {
            float mixed = 0.0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += ((buffer.get() & 0xff) - 128) / 128.0f;
            }
            output.append(clamp(mixed / channelCount));
        }
        return frameCount;
    }

    private static float clamp(final float value) {
        if (value > 1.0f) {
            return 1.0f;
        }
        if (value < -1.0f) {
            return -1.0f;
        }
        return value;
    }

    /** float 配列へサンプルを追加する簡易 builder です。 */
    public static final class FloatArrayBuilder {
        private float[] buffer = new float[16_000];
        private int size;

        /**
         * float PCM サンプルを 1 つ追加します。
         *
         * @param value 追加するサンプル。例: {@code 0.25f}
         * @return 追加後のサンプル数。例: {@code 16001}
         */
        public int append(final float value) {
            ensureCapacity(size + 1);
            buffer[size++] = value;
            return size;
        }

        /**
         * 現在の内容を配列として返します。
         *
         * @return 追加済みサンプルだけを持つ配列。例: {@code new float[]{0.25f}}
         */
        @NonNull
        public float[] toArray() {
            return Arrays.copyOf(buffer, size);
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
