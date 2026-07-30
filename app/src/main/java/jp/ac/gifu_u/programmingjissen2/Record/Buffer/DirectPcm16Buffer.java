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
     * 消費済みDirect PCM16領域を新しいbuffer wrapperで再利用します。
     * @param bytes Directかつ偶数byte容量の領域。例: {@code decodedAudio.samples()}
     * @return 同じ保存領域を指す空のPCM buffer。例: {@code wrapped.sampleCount() == 0}
     * @throws IllegalArgumentException 非Direct、空、または奇数byte容量の場合
     */
    @NonNull
    public static DirectPcm16Buffer wrap(@NonNull final ByteBuffer bytes) {
        if (!bytes.isDirect() || bytes.capacity() <= 0 || (bytes.capacity() & 1) != 0) {
            throw new IllegalArgumentException("invalid Direct PCM16 storage");
        }
        return new DirectPcm16Buffer(bytes);
    }

    /**
     * 既存領域を所有するwrapperを作ります。
     * @param bytes 検証済みDirect PCM16領域。例: {@code directBytes}
     * 戻り値は新しいinstanceで、例外はありません。
     */
    private DirectPcm16Buffer(@NonNull final ByteBuffer bytes) {
        this.bytes = bytes.order(ByteOrder.nativeOrder());
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
