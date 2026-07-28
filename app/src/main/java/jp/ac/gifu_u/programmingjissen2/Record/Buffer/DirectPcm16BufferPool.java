package jp.ac.gifu_u.programmingjissen2.Record.Buffer;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;

/** 同一容量のDirect PCM16チャンクを上限付きで再利用するスレッドセーフなプールです。 */
public final class DirectPcm16BufferPool {
    private final int sampleCapacity;
    private final int maxCachedBuffers;
    private final ArrayDeque<PooledPcmChunk> freeBuffers = new ArrayDeque<>();

    /**
     * Directバッファプールを作成します。
     * @param sampleCapacity 1チャンクの容量。例: {@code 8000}
     * @param maxCachedBuffers 保持上限。例: {@code 132}
     * @throws IllegalArgumentException いずれかが0以下の場合
     */
    public DirectPcm16BufferPool(final int sampleCapacity, final int maxCachedBuffers) {
        if (sampleCapacity <= 0 || maxCachedBuffers <= 0) {
            throw new IllegalArgumentException("pool capacities must be positive");
        }
        this.sampleCapacity = sampleCapacity;
        this.maxCachedBuffers = maxCachedBuffers;
    }

    /**
     * 空の所有権付きチャンクを取得します。
     * @return 再利用または新規確保したチャンク。例: {@code pool.acquire()}
     * @throws OutOfMemoryError Directメモリを新規確保できない場合
     */
    @NonNull
    public synchronized PooledPcmChunk acquire() {
        final PooledPcmChunk chunk = freeBuffers.pollFirst();
        if (chunk != null) {
            chunk.acquireForUse();
            return chunk;
        }
        return new PooledPcmChunk(this, new DirectPcm16Buffer(sampleCapacity));
    }

    /**
     * closeされたチャンクを再利用待ちへ戻します。
     * @param chunk このプールが生成したチャンク。例: {@code chunk}
     * @return キャッシュした場合true。上限到達時はfalse。例: {@code true}
     * @throws IllegalArgumentException 別プール由来のチャンクの場合
     */
    synchronized boolean release(@NonNull final PooledPcmChunk chunk) {
        if (!chunk.belongsTo(this)) {
            throw new IllegalArgumentException("chunk belongs to another pool");
        }
        chunk.buffer().clear();
        if (freeBuffers.size() >= maxCachedBuffers) {
            return false;
        }
        freeBuffers.addFirst(chunk);
        return true;
    }

    /** キャッシュ参照を破棄します。使用中チャンクには影響せず、戻り値と例外はありません。 */
    public synchronized void clear() {
        freeBuffers.clear();
    }
}
