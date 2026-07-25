package Utils.StringPool;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.function.Consumer;

import Utils.Pool.ObjectPool;

/**
 * 一時的な文字列生成で使う {@link StringBuilder} のスコープ付きラッパーです。
 * ラッパー自身をスレッドごとのプールで再利用します。
 *
 * <p>取得したインスタンスは、取得したスレッドの
 * try-with-resources 内だけで使用してください。ラッパー自身を再利用するため、
 * close後の参照を保持・操作すると、後続の貸出を誤って操作する可能性があります。</p>
 */
public final class StringBufferBuilderPool implements AutoCloseable {
    /** 巨大な内部バッファをプールへ残さないための上限です。 */
    public static final int MAX_CAPACITY = 4096;

    /** この貸出を所有するスレッドです。 */
    private Thread ownerThread;
    /** このラッパーが管理するStringBuilderです。 */
    private final StringBuilder stringBuilder = new StringBuilder();
    /** ラッパーを借りたプールです。 */
    private ObjectPool<StringBufferBuilderPool> ownerPool;
    /** close済みならtrueです。 */
    private boolean isClosed;

    /**
     * プールが初めて容量を確保するときにラッパーを生成します。
     *
     * @return 新しい未貸出ラッパー。例: {@code StringBufferBuilderPool builder}
     */
    StringBufferBuilderPool() {
        isClosed = true;
    }

    /**
     * プールから取得されたラッパーを現在スレッドの貸出状態へ初期化します。
     *
     * @param pool 借用元プール。例: {@code ObjectPool<StringBufferBuilderPool>}
     * @return なし。例: 呼び出し後はappend可能
     * @throws NullPointerException poolがnullの場合
     * @throws IllegalStateException 貸出中のラッパーがプールへ重複登録されていた場合
     */
    void PrepareForUse(@NonNull final ObjectPool<StringBufferBuilderPool> pool) {
        if (!isClosed) {
            throw new IllegalStateException("StringBufferBuilderPool is already leased");
        }
        ownerPool = Objects.requireNonNull(pool, "pool");
        ownerThread = Thread.currentThread();
        isClosed = false;
    }

    /**
     * 返却されたラッパーを空にし、巨大な内部バッファを解放します。
     *
     * @param builder 初期化するラッパー。例: {@code StringBufferBuilderPool builder}
     * @return なし。例: 再貸出時の長さは0
     * @throws NullPointerException builderがnullの場合
     */
    static void PrepareForPool(@NonNull final StringBufferBuilderPool builder) {
        Objects.requireNonNull(builder, "builder");
        builder.stringBuilder.setLength(0);
        if (builder.stringBuilder.capacity() > MAX_CAPACITY) {
            builder.stringBuilder.trimToSize();
        }
        builder.isClosed = true;
        builder.ownerThread = null;
        builder.ownerPool = null;
    }

    /**
     * 現在のスレッドが保持する未使用builderを破棄し、ThreadLocalからプールを外します。
     *
     * @return なし。例: {@code StringBufferBuilderPool.ClearCurrentThreadPool();}
     */
    public static void ClearCurrentThreadPool() {
        StringBuilderPoolStorage.ClearCurrentThreadPool();
    }

    /**
     * 現在のスレッド用プールからbuilderを借りた、新しい貸出ラッパーを取得します。
     *
     * @return 使用可能なラッパー。例: {@code StringBufferBuilderPool.GetBuilder()}
     */
    @NonNull
    public static StringBufferBuilderPool GetBuilder() {
        return StringBuilderPoolStorage.Acquire();
    }

    /**
     * builderを取得元プールへ返却します。
     *
     * @param builder 返却するラッパー。例: {@code StringBufferBuilderPool.GetBuilder()}
     * @return 通常容量のバッファを再利用した場合はtrue、二重返却または巨大バッファを
     *         解放した場合はfalse。例: {@code true}
     * @throws NullPointerException builderがnullの場合
     * @throws IllegalStateException 取得したスレッド以外から返却した場合
     */
    public static boolean TryRelease(@NonNull final StringBufferBuilderPool builder) {
        return Objects.requireNonNull(builder, "builder").ReleaseToOwnerPool();
    }

    /**
     * Runnableを、終了時に現在スレッドのbuilderプールを必ず破棄する処理で包みます。
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
     * 終了時にbuilderプールを必ず破棄するワーカースレッドを作成します。
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
     * 一時builderを使って文字列を作成します。
     * actionは受け取ったStringBuilderを呼び出し後に保持してはいけません。
     *
     * @param action builderへ文字列を追加する処理。例: {@code sb -> sb.append("text")}
     * @return 作成された文字列。例: {@code "text"}
     * @throws NullPointerException actionがnullの場合
     */
    @NonNull
    public static String Build(@NonNull final Consumer<StringBuilder> action) {
        Objects.requireNonNull(action, "action");
        try (final StringBufferBuilderPool pooled = GetBuilder()) {
            action.accept(pooled.stringBuilder);
            return pooled.stringBuilder.toString();
        }
    }

    /**
     * Iterableの各要素をseparatorで結合します。
     *
     * @param separator 要素間に入れる文字列。例: {@code ","}
     * @param items 結合する要素。例: {@code List.of("a", "b")}
     * @return 結合済み文字列。例: {@code "a,b"}
     * @throws NullPointerException separatorまたはitemsがnullの場合
     */
    @NonNull
    public static String Join(
            @NonNull final String separator,
            @NonNull final Iterable<?> items
    ) {
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(items, "items");
        try (final StringBufferBuilderPool pooled = GetBuilder()) {
            boolean first = true;
            for (Object item : items) {
                if (first) {
                    first = false;
                } else {
                    pooled.stringBuilder.append(separator);
                }
                pooled.stringBuilder.append(item);
            }
            return pooled.stringBuilder.toString();
        }
    }

    /**
     * Object配列の各要素をseparatorで結合します。
     *
     * @param separator 要素間に入れる文字列。例: {@code ","}
     * @param items 結合する要素。例: {@code new Object[]{"a", "b"}}
     * @return 結合済み文字列。例: {@code "a,b"}
     * @throws NullPointerException separatorまたはitemsがnullの場合
     */
    @NonNull
    public static String Join(
            @NonNull final String separator,
            @NonNull final Object... items
    ) {
        Objects.requireNonNull(separator, "separator");
        Objects.requireNonNull(items, "items");
        try (final StringBufferBuilderPool pooled = GetBuilder()) {
            boolean first = true;
            for (Object item : items) {
                if (first) {
                    first = false;
                } else {
                    pooled.stringBuilder.append(separator);
                }
                pooled.stringBuilder.append(item);
            }
            return pooled.stringBuilder.toString();
        }
    }

    /**
     * 現在の文字数を返します。
     *
     * @return 現在の文字数。例: {@code 4}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    public int length() {
        EnsureUsable();
        return stringBuilder.length();
    }

    /**
     * 文字列を追加します。
     *
     * @param text 追加する文字列。例: {@code "text"}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final String text) {
        EnsureUsable();
        stringBuilder.append(text);
        return this;
    }

    /**
     * 文字を追加します。
     *
     * @param value 追加する文字。例: {@code '\n'}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final char value) {
        EnsureUsable();
        stringBuilder.append(value);
        return this;
    }

    /**
     * 文字配列を追加します。
     *
     * @param text 追加する文字配列。例: {@code new char[]{'a', 'b'}}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final char[] text) {
        EnsureUsable();
        stringBuilder.append(text);
        return this;
    }

    /**
     * CharSequenceを追加します。
     *
     * @param text 追加する文字列。例: {@code new StringBuilder("text")}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final CharSequence text) {
        EnsureUsable();
        stringBuilder.append(text);
        return this;
    }

    /**
     * オブジェクトの文字列表現を追加します。
     *
     * @param object 追加する値。例: {@code 123}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final Object object) {
        EnsureUsable();
        stringBuilder.append(object);
        return this;
    }

    /**
     * CharSequenceの指定範囲を追加します。
     *
     * @param text 追加元。例: {@code "abcd"}
     * @param start 開始位置。例: {@code 1}
     * @param end 終了位置。例: {@code 3}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IndexOutOfBoundsException startまたはendが不正な場合
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(
            final CharSequence text,
            final int start,
            final int end
    ) {
        EnsureUsable();
        stringBuilder.append(text, start, end);
        return this;
    }

    /**
     * boolean値を追加します。
     *
     * @param value 追加する値。例: {@code true}
     * @return メソッドチェーン用のthis。例: {@code builder}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    public StringBufferBuilderPool append(final boolean value) {
        EnsureUsable();
        stringBuilder.append(value);
        return this;
    }

    /**
     * builderを取得元プールへ一度だけ返却します。
     *
     * @return 通常容量ならtrue、二重返却または巨大バッファを解放した場合はfalse。
     *         例: {@code true}
     * @throws IllegalStateException 取得したスレッド以外から呼んだ場合
     */
    private boolean ReleaseToOwnerPool() {
        if (isClosed) {
            return false;
        }
        EnsureOwnerThread();

        final boolean retainedBuffer = stringBuilder.capacity() <= MAX_CAPACITY;
        isClosed = true;
        final ObjectPool<StringBufferBuilderPool> releasePool = ownerPool;
        releasePool.releaseOrDelete(this);
        return retainedBuffer;
    }

    /**
     * 現在スレッドが取得元スレッドであることを検証します。
     *
     * @return なし。例: 取得元スレッドなら正常終了
     * @throws IllegalStateException 取得元と異なるスレッドから呼んだ場合
     */
    private void EnsureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "StringBufferBuilderPool must be used and closed by its owner thread"
            );
        }
    }

    /**
     * 現在の貸出が使用可能であることを検証します。
     *
     * @return なし。例: close前かつ取得元スレッドなら正常終了
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    private void EnsureUsable() {
        if (isClosed) {
            throw new IllegalStateException("StringBufferBuilderPool is already closed");
        }
        EnsureOwnerThread();
    }

    /**
     * builderを取得元プールへ返却します。二重closeは何もしません。
     *
     * @return なし。例: try-with-resources終了時に自動実行
     * @throws IllegalStateException 取得したスレッド以外から呼んだ場合
     */
    @Override
    public void close() {
        ReleaseToOwnerPool();
    }

    /**
     * 現在の内容をStringへ変換します。
     *
     * @return 現在の内容。例: {@code "text"}
     * @throws IllegalStateException 取得元と異なるスレッド、またはclose後に呼んだ場合
     */
    @NonNull
    @Override
    public String toString() {
        EnsureUsable();
        return stringBuilder.toString();
    }
}
