package jp.ac.gifu_u.programmingjissen2.Record.Buffer;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** PCM16をJavaヒープ外の固定長DirectByteBufferへ保持します。 */
public final class DirectPcm16Buffer {
    private final ByteBuffer bytes;
    private int sampleCount;

    /**
     * 指定容量のDirect PCM16バッファを確保します。
     * @param sampleCapacity サンプル容量。例: {@code 8000}
     * @throws IllegalArgumentException 容量が0以下、またはbyte数がint範囲を超える場合
     */
    public DirectPcm16Buffer(final int sampleCapacity) {
        if (sampleCapacity <= 0 || sampleCapacity > Integer.MAX_VALUE / Short.BYTES) {
            throw new IllegalArgumentException("invalid PCM16 sample capacity: " + sampleCapacity);
        }
        bytes = ByteBuffer.allocateDirect(sampleCapacity * Short.BYTES)
                .order(ByteOrder.nativeOrder());
    }

    /**
     * native byte orderのDirectByteBufferを返します。
     * @return 同じ保存領域を指すバッファ。例: {@code buffer.bytes().isDirect() == true}
     */
    @NonNull
    public ByteBuffer bytes() {
        return bytes;
    }

    /** @return 最大サンプル数。例: {@code 8000}。例外はありません。 */
    public int sampleCapacity() {
        return bytes.capacity() / Short.BYTES;
    }

    /** @return 有効サンプル数。例: {@code 4000}。例外はありません。 */
    public int sampleCount() {
        return sampleCount;
    }

    /**
     * AudioRecordなどが書き込んだ有効サンプル数を設定します。
     * @param value 有効数。例: {@code 4000}
     * @throws IllegalArgumentException 0未満、または容量を超える場合
     */
    public void setSampleCount(final int value) {
        if (value < 0 || value > sampleCapacity()) {
            throw new IllegalArgumentException("sampleCount is outside buffer");
        }
        sampleCount = value;
    }

    /** 有効長とposition/limitを初期化します。戻り値と例外はありません。 */
    public void clear() {
        sampleCount = 0;
        bytes.clear();
    }
}
