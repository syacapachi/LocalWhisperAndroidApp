package jp.ac.gifu_u.programmingjissen2.Record.Buffer;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct PCMチャンクの単独所有権とプール返却を表します。 */
public final class PooledPcmChunk implements AutoCloseable {
    private final DirectPcm16BufferPool owner;
    private final DirectPcm16Buffer buffer;
    private final AtomicBoolean released = new AtomicBoolean();

    PooledPcmChunk(
            @NonNull final DirectPcm16BufferPool owner,
            @NonNull final DirectPcm16Buffer buffer
    ) {
        this.owner = owner;
        this.buffer = buffer;
    }

    /** @return PCM保存領域。例: {@code chunk.bytes().isDirect() == true} */
    @NonNull
    public ByteBuffer bytes() {
        ensureOwned();
        return buffer.bytes();
    }

    /** @return 有効サンプル数。例: {@code 8000} */
    public int sampleCount() {
        ensureOwned();
        return buffer.sampleCount();
    }

    /**
     * 有効サンプル数を設定します。
     * @param value 有効数。例: {@code 8000}
     * @throws IllegalStateException 返却済みの場合
     * @throws IllegalArgumentException 容量外の場合
     */
    public void setSampleCount(final int value) {
        ensureOwned();
        buffer.setSampleCount(value);
    }

    /** @return プール内部の保存オブジェクト。例: {@code chunk.buffer()}。例外はありません。 */
    DirectPcm16Buffer buffer() {
        return buffer;
    }

    /**
     * @param pool 確認対象。例: {@code owner}
     * @return このプール由来ならtrue。例: {@code true}。例外はありません。
     */
    boolean belongsTo(final DirectPcm16BufferPool pool) {
        return owner == pool;
    }

    /**
     * プール待機状態から使用中へ戻します。
     * 引数と戻り値はありません。
     * @throws IllegalStateException すでに使用中の場合
     */
    void acquireForUse() {
        if (!released.compareAndSet(true, false)) {
            throw new IllegalStateException("pooled chunk was already acquired");
        }
        buffer.clear();
    }

    /** 所有権をプールへ一度だけ返します。戻り値はなく、複数回呼んでも安全です。 */
    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            owner.release(this);
        }
    }

    /**
     * 呼び出し側がまだ所有中か検証します。
     * 引数と戻り値はありません。
     * @throws IllegalStateException close後の場合
     */
    private void ensureOwned() {
        if (released.get()) {
            throw new IllegalStateException("PCM chunk is already released");
        }
    }
}
