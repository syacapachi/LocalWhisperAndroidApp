package Utils;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;

/**
 * try-with-resources で使えるスコープ付きユーティリティを提供します。
 */
public final class ScopableUtility {
    private ScopableUtility() {
    }

    /**
     * スコープ終了時に自動で返却される pooled builder を取得します。
     *
     * @return {@link AutoCloseable} な pooled builder。
     *         例: {@code StringBufferBuilderPool builder}
     */
    @NonNull
    public static StringBufferBuilderPool getBuilder() {
        return StringBufferBuilderPool.GetBuilder();
    }
}
