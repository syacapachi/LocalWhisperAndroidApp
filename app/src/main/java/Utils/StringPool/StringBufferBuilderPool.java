package Utils.StringPool;

import android.util.Log;

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

    /** 再利用する {@link StringBuilder} を保持するプールです。 */
    private static final ObjectPool<StringBuilder> builderPool
            = new ObjectPool<>(
            StringBuilder::new,
            null,
            StringBufferBuilderPool::ClearBuilder,
            null,
            10,
            100
    );

    private StringBufferBuilderPool() {
    }

    /**
     * プールへ返却する前に builder の内容を空にします。
     *
     * @param builder 初期化する builder
     */
    private static void ClearBuilder(StringBuilder builder) {
        builder.setLength(0);
    }

    /**
     * プールから {@link StringBuilder} を取得します。
     *
     * @return 使用可能な builder
     */
    public static StringBuilder GetBuilder() {
        return builderPool.getOrCreate();
    }

    /**
     * ビルダーを返却します。
     * 巨大なものは破棄されます。
     * @param builder 返却する builder
     */
    public static void Release(StringBuilder builder) {
        if (builder == null) {
            return;
        }
        if (builder.capacity() > MAX_CAPACITY) {
            return;
        }
        builderPool.releaseOrDelete(builder);
    }

    /**
     * builder の内容を文字列に変換し、builder をプールへ返却します。
     *
     * @param builder 文字列化して返却する builder
     * @return builder の内容
     */
    public static String ToStringAndRelease(StringBuilder builder) {
        String str = builder.toString();
        Release(builder);
        return str;
    }

    /**
     * 一時 builder を使って文字列を作成します。
     *
     * @param action builder に文字列要素を追加する処理
     * @return 作成された文字列
     */
    public static String Build(Consumer<StringBuilder> action) {
        StringBuilder sb = GetBuilder();

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
    public static String Join(String separator, Iterable<?> items) {
        StringBuilder sb = GetBuilder();
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
    public static String Join(String separator, Object... items){
        StringBuilder sb = GetBuilder();
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
