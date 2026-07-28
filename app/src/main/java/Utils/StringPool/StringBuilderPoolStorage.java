package Utils.StringPool;

import androidx.annotation.NonNull;

import Utils.Pool.ObjectPool;

/**
 * StringBufferBuilderPool自身を再利用するスレッドローカルプールの非公開ユーティリティです。
 */
final class StringBuilderPoolStorage {
    /** 現在のスレッドだけが利用するラッパープールです。 */
    private static final ThreadLocal<ObjectPool<StringBufferBuilderPool>> builderPool =
            new ThreadLocal<>();

    /**
     * インスタンス化を禁止します。
     *
     * @throws AssertionError リフレクションなどで呼び出した場合
     */
    private StringBuilderPoolStorage() {
        throw new AssertionError("No instances");
    }

    /**
     * 現在のスレッド用プールを取得し、未作成なら初期化します。
     *
     * @return 呼び出しスレッド専用のプール。
     *         例: {@code ObjectPool<StringBufferBuilderPool>}
     */
    @NonNull
    private static ObjectPool<StringBufferBuilderPool> GetCurrentThreadPool() {
        ObjectPool<StringBufferBuilderPool> pool = builderPool.get();
        if (pool == null) {
            pool = new ObjectPool<>(
                    StringBufferBuilderPool::new,
                    null,
                    StringBufferBuilderPool::PrepareForPool,
                    null,
                    2,
                    100
            );
            builderPool.set(pool);
        }
        return pool;
    }

    /**
     * 現在のスレッド用プールからラッパーを取得し、貸出状態へ初期化します。
     *
     * @return 使用可能なラッパー。例: {@code StringBufferBuilderPool builder}
     */
    @NonNull
    static StringBufferBuilderPool Acquire() {
        final ObjectPool<StringBufferBuilderPool> pool = GetCurrentThreadPool();
        final StringBufferBuilderPool builder = pool.getOrCreate();
        builder.PrepareForUse(pool);
        return builder;
    }

    /**
     * 現在のスレッドが保持する未使用builderを破棄し、ThreadLocalからプールを外します。
     *
     * @return なし。例: 呼び出し後の次回取得時に新しいプールを生成
     */
    static void ClearCurrentThreadPool() {
        final ObjectPool<StringBufferBuilderPool> pool = builderPool.get();
        if (pool != null) {
            pool.clearPool();
            builderPool.remove();
        }
    }
}
