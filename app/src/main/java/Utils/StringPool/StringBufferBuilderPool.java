package Utils.StringPool;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Consumer;

import Utils.Pool.ObjectPool;

/**
 * 一時的な文字列生成で使う {@link StringBuilder} を再利用するためのプールです。
 */
public final class StringBufferBuilderPool {
    private static final String TAG = StringBufferBuilderPool.class.getSimpleName();
    /**
     * 巨大バッファを作らないための上限
     */
    private static final int MAX_CAPACITY = 4096;

    /** 現在のスレッドだけが利用する {@link StringBuilder} プールです。 */
    private static final ThreadLocal<ObjectPool<StringBuilder>> builderPool = new ThreadLocal<>();

    private StringBufferBuilderPool() {
    }

    /**
     * プールへ返却する前に builder の内容を空にします。
     *
     * @param builder 初期化する builder
     */
    private static void ClearBuilder(@NonNull final StringBuilder builder) {
        builder.setLength(0);
    }

    /**
     * 現在のスレッド用プールを取得し、未作成なら初期化します。
     *
     * @return 呼び出しスレッド専用のプール。例: {@code ObjectPool<StringBuilder>}
     */
    @NonNull
    private static ObjectPool<StringBuilder> GetCurrentThreadPool() {
        ObjectPool<StringBuilder> pool = builderPool.get();
        if (pool == null) {
            pool = new ObjectPool<>(
                    StringBuilder::new,
                    null,
                    StringBufferBuilderPool::ClearBuilder,
                    null,
                    2,
                    100
            );
            builderPool.set(pool);
        }
        return pool;
    }

    /**
     * プールから {@link StringBuilder} を取得します。
     *
     * @return 使用可能な builder
     */
    @NonNull
    public static StringBuilder GetBuilder() {
        return GetCurrentThreadPool().getOrCreate();
    }

    /**
     * ビルダーを返却します。
     * 巨大なものは破棄されます。
     * @param builder 返却する builder
     */
    public static void Release(@NonNull final StringBuilder builder) {
        if (builder.capacity() > MAX_CAPACITY) {
            return;
        }
        GetCurrentThreadPool().releaseOrDelete(builder);
    }

    /**
     * 現在のスレッドが保持する全builderを破棄し、ThreadLocalからプールを外します。
     * スレッド終了用ラッパーのfinallyから呼び出すことで、例外終了時にも解放されます。
     *
     * @return なし。例: {@code StringBufferBuilderPool.ClearCurrentThreadPool();}
     */
    public static void ClearCurrentThreadPool() {
        final ObjectPool<StringBuilder> pool = builderPool.get();
        if (pool != null) {
            pool.clearPool();
            builderPool.remove();
        }
    }

    /**
     * Runnable終了時に現在スレッドのStringBuilderプールを必ず破棄する処理で包みます。
     *
     * @param action 実行対象。例: {@code worker::run}
     * @return finallyでプールを破棄するRunnable。例: {@code Runnable cleanupAction}
     * @throws NullPointerException actionがnullの場合
     */
    @NonNull
    public static Runnable WrapWithThreadPoolCleanup(@NonNull final Runnable action) {
        Objects.requireNonNull(action, "action");
        return () -> {
            try {
                action.run();
            } finally {
                ClearCurrentThreadPool();
            }
        };
    }

    /**
     * 終了時にStringBuilderプールを必ず破棄するワーカースレッドを作成します。
     * 呼び出し側は戻り値へ {@link Thread#start()} を呼びます。
     *
     * @param action スレッドで実行する処理。例: {@code worker::run}
     * @param name スレッド名。例: {@code "WhisperTranscriptionWorker"}
     * @return 未開始のThread。例: {@code Thread[WhisperTranscriptionWorker]}
     * @throws NullPointerException actionまたはnameがnullの場合
     */
    @NonNull
    public static Thread NewThreadWithPoolCleanup(
            @NonNull final Runnable action,
            @NonNull final String name
    ) {
        Objects.requireNonNull(name, "name");
        return new Thread(WrapWithThreadPoolCleanup(action), name);
    }

    /**
     * builder の内容を文字列に変換し、builder をプールへ返却します。
     *
     * @param builder 文字列化して返却する builder
     * @return builder の内容
     */
    @NonNull
    public static String ToStringAndRelease(@NonNull final StringBuilder builder) {
        final String str = builder.toString();
        Release(builder);
        return str;
    }

    /**
     * 一時 builder を使って文字列を作成します。
     *
     * @param action builder に文字列要素を追加する処理
     * @return 作成された文字列
     */
    @NonNull
    public static String Build(@NonNull final Consumer<StringBuilder> action) {
        final StringBuilder sb = GetBuilder();

        try {
            action.accept(sb);
            return sb.toString();
        } finally {
            Release(sb);
        }
    }

    /**
     * Iterable の各要素を separator で結合します。
     *
     * @param separator 要素間に入れる文字列
     * @param items 結合する要素
     * @return 結合済み文字列
     */
    @NonNull
    public static String Join(@NonNull final String separator, @NonNull final Iterable<?> items) {
        final StringBuilder sb = GetBuilder();
        boolean first = true;
        try {
            for (Object item : items) {
                if (first) {
                    first = false;
                } else {
                    sb.append(separator);
                }
                sb.append(item);
            }
            return sb.toString();
        } finally {
            Release(sb);
        }
    }
    /**
     * Object の各要素を separator で結合します。
     *
     * @param separator 要素間に入れる文字列
     * @param items 結合する要素
     * @return 結合済み文字列
     */
    @NonNull
    public static String Join(@NonNull final String separator, @NonNull final  Object... items){
        final StringBuilder sb = GetBuilder();
        boolean first = true;
        try {
            for (Object item : items) {
                if (first) {
                    first = false;
                } else {
                    sb.append(separator);
                }
                sb.append(item);
            }
            return sb.toString();
        }finally {
            Release(sb);
        }
    }
}
