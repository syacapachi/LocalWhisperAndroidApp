package jp.ac.gifu_u.programmingjissen2.Record.Buffer;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 推論窓を前詰めせず保持し、末尾折り返しを2区間として公開するDirectリングバッファです。 */
public final class DirectPcm16RingBuffer {
    private final ByteBuffer storage;
    private final int capacitySamples;
    private int headSample;
    private int sizeSamples;

    /**
     * Directリングバッファを作成します。
     * @param capacitySamples 容量。例: {@code 160000}
     * @throws IllegalArgumentException 容量が0以下、またはbyte数がint範囲を超える場合
     */
    public DirectPcm16RingBuffer(final int capacitySamples) {
        if (capacitySamples <= 0 || capacitySamples > Integer.MAX_VALUE / Short.BYTES) {
            throw new IllegalArgumentException("invalid ring capacity");
        }
        this.capacitySamples = capacitySamples;
        storage = ByteBuffer.allocateDirect(capacitySamples * Short.BYTES)
                .order(ByteOrder.nativeOrder());
    }

    /**
     * Direct PCMの先頭から指定数を末尾へコピーします。
     * @param source Direct入力。例: {@code chunk.bytes()}
     * @param sampleCount コピー数。例: {@code 8000}
     * @return 追加数。例: {@code 8000}
     * @throws IllegalArgumentException 非Direct、負数、入力範囲外の場合
     * @throws IllegalStateException 空き容量が不足する場合
     */
    public int append(@NonNull final ByteBuffer source, final int sampleCount) {
        if (!source.isDirect() || sampleCount < 0
                || sampleCount > source.capacity() / Short.BYTES) {
            throw new IllegalArgumentException("invalid direct PCM source");
        }
        if (sampleCount > capacitySamples - sizeSamples) {
            throw new IllegalStateException("PCM ring buffer is full");
        }
        final int tailSample = (headSample + sizeSamples) % capacitySamples;
        final int firstSamples = Math.min(sampleCount, capacitySamples - tailSample);
        copyBytes(source, 0, storage, tailSample * Short.BYTES,
                firstSamples * Short.BYTES);
        final int secondSamples = sampleCount - firstSamples;
        if (secondSamples > 0) {
            copyBytes(source, firstSamples * Short.BYTES, storage, 0,
                    secondSamples * Short.BYTES);
        }
        sizeSamples += sampleCount;
        return sampleCount;
    }

    /** @return 有効サンプル数。例: {@code 80000}。例外はありません。 */
    public int size() {
        return sizeSamples;
    }

    /** @return JNIへ渡す同一Direct保存領域。例: {@code ring.storage()} */
    @NonNull
    public ByteBuffer storage() {
        return storage;
    }

    /**
     * 指定窓の第1区間byte offsetを返します。
     * @param requestedSamples 窓長。例: {@code 80000}
     * @return byte offset。例: {@code 64000}
     * @throws IllegalArgumentException 窓長が有効範囲外の場合
     */
    public int firstByteOffset(final int requestedSamples) {
        validateWindow(requestedSamples);
        return headSample * Short.BYTES;
    }

    /**
     * 指定窓の第1区間サンプル数を返します。
     * @param requestedSamples 窓長。例: {@code 80000}
     * @return 末尾までの連続数。例: {@code 48000}
     * @throws IllegalArgumentException 窓長が有効範囲外の場合
     */
    public int firstSampleCount(final int requestedSamples) {
        validateWindow(requestedSamples);
        return Math.min(requestedSamples, capacitySamples - headSample);
    }

    /**
     * 指定窓の第2区間byte offsetを返します。
     * @param requestedSamples 窓長。例: {@code 80000}
     * @return 折り返し先の0。第2区間なしでも0
     * @throws IllegalArgumentException 窓長が有効範囲外の場合
     */
    public int secondByteOffset(final int requestedSamples) {
        validateWindow(requestedSamples);
        return 0;
    }

    /**
     * 指定窓の第2区間サンプル数を返します。
     * @param requestedSamples 窓長。例: {@code 80000}
     * @return 折り返し後の数。例: {@code 32000}
     * @throws IllegalArgumentException 窓長が有効範囲外の場合
     */
    public int secondSampleCount(final int requestedSamples) {
        return requestedSamples - firstSampleCount(requestedSamples);
    }

    /**
     * 先頭を移動して処理済みPCMを破棄します。
     * @param sampleCount 破棄数。例: {@code 64000}
     * @return 実際の破棄数。例: {@code 64000}
     * @throws IllegalArgumentException 0未満、または有効長超過の場合
     */
    public int discardFirst(final int sampleCount) {
        if (sampleCount < 0 || sampleCount > sizeSamples) {
            throw new IllegalArgumentException("discard count is outside ring");
        }
        headSample = (headSample + sampleCount) % capacitySamples;
        sizeSamples -= sampleCount;
        if (sizeSamples == 0) {
            headSample = 0;
        }
        return sampleCount;
    }

    /** 有効長と読み取り位置を初期化します。戻り値と例外はありません。 */
    public void clear() {
        headSample = 0;
        sizeSamples = 0;
    }

    /**
     * 参照予定の窓が有効長内か検証します。
     * @param requestedSamples 窓長。例: {@code 80000}
     * @throws IllegalArgumentException 負数または有効長超過の場合
     */
    private void validateWindow(final int requestedSamples) {
        if (requestedSamples < 0 || requestedSamples > sizeSamples) {
            throw new IllegalArgumentException("requested window is outside ring");
        }
    }

    /**
     * ByteBufferのpositionを変えず指定byte区間をコピーします。
     * @param source コピー元。例: {@code chunk.bytes()}
     * @param sourceOffset コピー元byte位置。例: {@code 0}
     * @param destination コピー先。例: {@code ring.storage()}
     * @param destinationOffset コピー先byte位置。例: {@code 16000}
     * @param byteCount byte数。例: {@code 32000}
     * @throws IllegalArgumentException offset/limitがバッファ範囲外の場合
     * @throws java.nio.ReadOnlyBufferException コピー先が読み取り専用の場合
     */
    private static void copyBytes(
            final ByteBuffer source,
            final int sourceOffset,
            final ByteBuffer destination,
            final int destinationOffset,
            final int byteCount
    ) {
        if (byteCount == 0) {
            return;
        }
        final ByteBuffer read = source.duplicate();
        read.position(sourceOffset).limit(sourceOffset + byteCount);
        final ByteBuffer write = destination.duplicate();
        write.position(destinationOffset).limit(destinationOffset + byteCount);
        write.put(read);
    }
}
