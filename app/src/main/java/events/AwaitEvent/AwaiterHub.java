package events.AwaitEvent;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.function.Supplier;

import Utils.Pool.ObjectPool;
import Utils.StringPool.StringBufferBuilderPool;
import events.Request.PermissionAwaiter;
import events.Threading.ThreadStopAwaiter;
import events.Whisper.WhisperTranscriptionAwaiter;

/**
 * IAwaiterをObjectPoolで管理するクラス。
 */
public final class AwaiterHub {

    private static final HashMap<Class<? extends IAwaiter>, ObjectPool<? extends IAwaiter>> objectPoolDic = new HashMap<>();
    private AwaiterHub(){}
    static {
        //先に登録
        AwaiterHub.register(
                PermissionAwaiter.class,
                PermissionAwaiter::new
        );
        AwaiterHub.register(
                WhisperTranscriptionAwaiter.class,
                WhisperTranscriptionAwaiter::new
        );
        AwaiterHub.register(
                ThreadStopAwaiter.class,
                ThreadStopAwaiter::new
        );
    }
    /**
     * Awaiterを貸す
     * @param clazz 貸すクラス(インターフェース、抽象クラスはダメ)
     * @return 貸すクラスの実体
     * @param <T> IAwaiterを持つクラス
     * @throws IllegalStateException ObjectPoolを初期化(register)していない場合。
     */
    @NonNull
    public static <T extends IAwaiter> T rentAwaiter(final Class<T> clazz) throws IllegalStateException {
        @SuppressWarnings("unchecked")
        final ObjectPool<T> pool = (ObjectPool<T>) objectPoolDic.get(clazz);
        if(pool == null){
            throwIllegalStateException(
                    buildMessage(clazz.getName(), " is not registered."));
        }
        return pool.getOrCreate();
    }

    /**
     *
     * @param clazz 登録するAwaiter
     * @param onCreate 初期化関数(コンストラクタ入れるのを推奨)
     * @param <T> IAwaiter
     * @throws IllegalStateException 同じAwaiterが既に登録されている場合。
     */
    public static <T extends IAwaiter> void register(final Class<T> clazz, final Supplier<T> onCreate) throws IllegalStateException{
        if(objectPoolDic.containsKey(clazz)){
            throwIllegalStateException(
                    buildMessage(clazz.getName(), " already registered."));
        }
        final ObjectPool<T> pool = new ObjectPool<T>(
                onCreate,
                null,
                IAwaiter::reset,
                null,
                2,
                100

        );
        objectPoolDic.put(clazz,pool);
    }

    /**
     *
     * @param awaiter 返却するAwaiter
     * @param <T> IAwaiter
     * @throws IllegalStateException ObjectPoolを初期化(register)していない場合。
     */
    public static <T extends IAwaiter> void release(@NonNull final T awaiter) throws IllegalStateException {
        @SuppressWarnings("unchecked")
        final ObjectPool<T> pool = (ObjectPool<T>) objectPoolDic.get(awaiter.getClass());
        if(pool == null){
            throwIllegalStateException(
                    buildMessage(awaiter.getClass().getName(), " is not registered."));
        }
        pool.releaseOrDelete(awaiter);
    }
    public static  void clear(){
        for (ObjectPool<? extends IAwaiter> pool : objectPoolDic.values()) {
            pool.clearPool();
        }
        objectPoolDic.clear();
    }
    private static void throwIllegalStateException(@NonNull final String message) throws IllegalStateException{
        throw new IllegalStateException(message);
    }

    /**
     * 例外メッセージを {@link StringBufferBuilderPool#Join(String, Object...)} で作成します。
     *
     * @param value 先頭に入れる値
     * @param suffix 後ろに追加する文字列
     * @return 結合済みメッセージ
     */
    @NonNull
    private static String buildMessage(final Object value, final String suffix) {
        return StringBufferBuilderPool.Join("", value, suffix);
    }
}
