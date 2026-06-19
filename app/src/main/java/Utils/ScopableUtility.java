package Utils;

import Utils.StringPool.PooledStringBuilder;

/**
 * try-with-resources で使えるスコープ付きユーティリティを提供します。
 */
public final class ScopableUtility {
    private ScopableUtility() {
    }

    /**
     * スコープ終了時に自動で返却される pooled builder を取得します。
     *
     * @return {@link AutoCloseable} な pooled builder
     */
    public static PooledStringBuilder getBuilder() {
        return new PooledStringBuilder();
    }
}
