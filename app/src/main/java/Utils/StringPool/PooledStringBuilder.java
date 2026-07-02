package Utils.StringPool;

import androidx.annotation.NonNull;

/**
 * String Builderをプールするクラス。
 * try(){}でつかえる。
 */
public class PooledStringBuilder implements AutoCloseable{
    /** close 済みなら true。二重返却を防ぐために使います。 */
    private boolean isClosed = false;

    /** プールから借りている実体の builder です。 */
    private final StringBuilder builder;

    /**
     * プールから builder を借りて wrapper を作成します。
     */
    public PooledStringBuilder() {
        builder = StringBufferBuilderPool.GetBuilder();
    }

    /**
     * 内部の {@link StringBuilder} を直接取得します。
     *
     * @return プールから借りている builder
     */
    @NonNull
    public StringBuilder get() {
        return builder;
    }

    /**
     * 任意の値を builder に追加します。
     *
     * @param value 追加する値
     * @return メソッドチェーン用の this
     */
    @NonNull
    public PooledStringBuilder append(final Object value) {
        builder.append(value);
        return this;
    }

    /**
     * 文字列を builder に追加します。
     *
     * @param value 追加する文字列
     * @return メソッドチェーン用の this
     */
    @NonNull
    public PooledStringBuilder append(final String value) {
        builder.append(value);
        return this;
    }

    /**
     * 現在の文字数を返します。
     *
     * @return builder の長さ
     */
    public int length() {
        return builder.length();
    }

    /**
     * builder の内容を空にします。
     */
    public void clear(){
        builder.setLength(0);
    }

    /**
     * 現在の builder 内容を文字列として返します。
     *
     * @return builder の内容
     */
    @NonNull
    @Override
    public String toString() {
        return builder.toString();
    }

    /**
     * builder をプールへ返却します。
     */
    @Override
    public void close() {
        if(isClosed){
            return;
        }
        isClosed = true;
        StringBufferBuilderPool.Release(builder);
    }
}
