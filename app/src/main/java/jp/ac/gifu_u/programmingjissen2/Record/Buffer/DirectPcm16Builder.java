package jp.ac.gifu_u.programmingjissen2.Record.Buffer;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;
import java.util.ArrayList;

import jp.ac.gifu_u.programmingjissen2.Record.Utility.Pcm16AudioConverter;

/** 長さが事前に分からないデコードPCMを固定長Directチャンクへ蓄積します。 */
public final class DirectPcm16Builder {
    private static final int DEFAULT_CHUNK_SAMPLES = 16_000;

    private final int chunkSamples;
    private final ArrayList<DirectPcm16Buffer> chunks = new ArrayList<>();
    private final ArrayList<ShortBuffer> shortViews = new ArrayList<>();
    private int size;

    /** 1秒単位の既定チャンクで空のbuilderを作ります。戻り値と例外はありません。 */
    public DirectPcm16Builder() {
        this(DEFAULT_CHUNK_SAMPLES);
    }

    /**
     * 固定チャンク長を指定してbuilderを作ります。
     * @param chunkSamples 1チャンクのサンプル数。例: {@code 16000}
     * @throws IllegalArgumentException 0以下の場合
     */
    public DirectPcm16Builder(final int chunkSamples) {
        if (chunkSamples <= 0) {
            throw new IllegalArgumentException("chunkSamples must be positive");
        }
        this.chunkSamples = chunkSamples;
    }

    /**
     * PCM16サンプルを末尾へ追加します。
     * @param value 追加値。例: {@code (short) 8192}
     * @return 追加後の総数。例: {@code 16001}
     * @throws OutOfMemoryError 新しいDirectチャンクを確保できない場合
     */
    public int append(final short value) {
        final int chunkIndex = size / chunkSamples;
        final int indexInChunk = size % chunkSamples;
        ensureChunk(chunkIndex);
        shortViews.get(chunkIndex).put(indexInChunk, value);
        size++;
        chunks.get(chunkIndex).setSampleCount(indexInChunk + 1);
        return size;
    }

    /** @return 現在のサンプル数。例: {@code 44100}。例外はありません。 */
    public int size() {
        return size;
    }

    /** 蓄積値を破棄し、確保済みDirectチャンクを再利用可能にします。戻り値と例外はありません。 */
    public void clear() {
        size = 0;
        for (DirectPcm16Buffer chunk : chunks) {
            chunk.clear();
        }
    }

    /**
     * 蓄積PCMを指定レートへ線形補間し、連続したDirectバッファを返します。
     * @param sourceSampleRate 入力Hz。例: {@code 44100}
     * @param targetSampleRate 出力Hz。例: {@code 16000}
     * @return 有効長設定済みDirect PCM。例: {@code result.sampleCount() == 16000}
     * @throws IllegalArgumentException レートが0以下の場合
     * @throws OutOfMemoryError 出力Directバッファを確保できない場合
     */
    @NonNull
    public DirectPcm16Buffer resample(
            final int sourceSampleRate,
            final int targetSampleRate
    ) {
        final int outputLength = Pcm16AudioConverter.resampledLength(
                size, sourceSampleRate, targetSampleRate);
        final DirectPcm16Buffer output = new DirectPcm16Buffer(Math.max(1, outputLength));
        resampleInto(sourceSampleRate, targetSampleRate, output);
        return output;
    }

    /**
     * 蓄積PCMを指定レートへ線形補間し、既存Directバッファへ書き込みます。
     * @param sourceSampleRate 入力Hz。例: {@code 48000}
     * @param targetSampleRate 出力Hz。例: {@code 16000}
     * @param output 再利用する出力先。例: {@code new DirectPcm16Buffer(16000)}
     * @return 出力したサンプル数。例: {@code 16000}
     * @throws IllegalArgumentException レートが0以下、または出力容量が不足する場合
     */
    public int resampleInto(
            final int sourceSampleRate,
            final int targetSampleRate,
            @NonNull final DirectPcm16Buffer output
    ) {
        final int outputLength = Pcm16AudioConverter.resampledLength(
                size, sourceSampleRate, targetSampleRate);
        if (outputLength > output.sampleCapacity()) {
            throw new IllegalArgumentException("resampled PCM exceeds output capacity");
        }
        output.clear();
        final ShortBuffer outputSamples = output.bytes().asShortBuffer();
        if (outputLength == 0) {
            output.setSampleCount(0);
            return 0;
        }
        if (sourceSampleRate == targetSampleRate) {
            for (int index = 0; index < size; index++) {
                outputSamples.put(index, get(index));
            }
        } else {
            final double sourcePerTarget = (double) sourceSampleRate / targetSampleRate;
            for (int index = 0; index < outputLength; index++) {
                final double sourceIndex = index * sourcePerTarget;
                final int left = Math.min((int) sourceIndex, size - 1);
                final int right = Math.min(left + 1, size - 1);
                final double fraction = sourceIndex - left;
                final short leftValue = get(left);
                final short rightValue = get(right);
                final int interpolated = (int) Math.round(
                        leftValue + (rightValue - leftValue) * fraction);
                outputSamples.put(index, clampToShort(interpolated));
            }
        }
        output.setSampleCount(outputLength);
        return outputLength;
    }

    /**
     * 指定位置のPCMを返します。
     * @param index 0始まり位置。例: {@code 15999}
     * @return PCM16値。例: {@code 8192}
     * @throws IndexOutOfBoundsException 位置が有効長外の場合
     */
    public short get(final int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("PCM index: " + index);
        }
        return shortViews.get(index / chunkSamples).get(index % chunkSamples);
    }

    /**
     * 指定番号までDirectチャンクを確保します。
     * @param chunkIndex 0始まり番号。例: {@code 2}
     * @throws OutOfMemoryError Directメモリを確保できない場合
     */
    private void ensureChunk(final int chunkIndex) {
        while (chunks.size() <= chunkIndex) {
            final DirectPcm16Buffer chunk = new DirectPcm16Buffer(chunkSamples);
            chunks.add(chunk);
            shortViews.add(chunk.bytes().asShortBuffer());
        }
    }

    /**
     * 整数をPCM16範囲へ丸めます。
     * @param value 入力。例: {@code 40000}
     * @return PCM16値。例: {@code 32767}。例外はありません。
     */
    private static short clampToShort(final int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
