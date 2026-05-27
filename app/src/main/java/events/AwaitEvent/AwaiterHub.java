package events.AwaitEvent;

import java.util.HashMap;
import java.util.function.Supplier;

import Utils.Pool.ObjectPool;
import events.Request.PermissionAwaiter;

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
    }
    /**
     * Awaiterを貸す
     * @param clazz 貸すクラス(インターフェース、抽象クラスはダメ)
     * @return 貸すクラスの実体
     * @param <T> IAwaiterを持つクラス
     * @throws IllegalStateException ObjectPoolを初期化(register)していない場合。
     */
    public static <T extends IAwaiter> T rentAwaiter(Class<T> clazz) throws IllegalStateException {
        @SuppressWarnings("unchecked")
        ObjectPool<T> pool = (ObjectPool<T>) objectPoolDic.get(clazz);
        if(pool == null){
            throw new IllegalStateException(
                    clazz.getName() + " is not registered.");
        }
        return pool.getOrCreate();
    }

    /**
     *
     * @param clazz 登録するAwaiter
     * @param onCreate 初期化関数(コンストラクタ入れて)
     * @param <T> IAwaiter
     * @throws IllegalStateException 同じAwaiterが既に登録されている場合。
     */
    public static <T extends IAwaiter> void register(Class<T> clazz, Supplier<T> onCreate) throws IllegalStateException{
        if(objectPoolDic.containsKey(clazz)){
            throw new IllegalStateException(
                    clazz.getName() + " already registered.");
        }
        ObjectPool<T> pool = new ObjectPool<T>(
                onCreate,
                null,
                IAwaiter::reset,
                null,
                10,
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
    public static <T extends IAwaiter> void release(T awaiter) throws IllegalStateException {
        @SuppressWarnings("unchecked")
        ObjectPool<T> pool = (ObjectPool<T>) objectPoolDic.get(awaiter.getClass());
        if(pool == null){
            throw new IllegalStateException(
                    awaiter.getClass().getName() + " is not registered.");
        }
        pool.releaseOrDelete(awaiter);
    }
}
