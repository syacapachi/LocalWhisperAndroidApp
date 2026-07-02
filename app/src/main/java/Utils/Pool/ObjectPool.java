package Utils.Pool;

import android.nfc.Tag;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import Utils.StringPool.StringBufferBuilderPool;

/**
 * オブジェクトをキューを使って管理します。
 * @param <T> 管理したいクラス
 */
public class ObjectPool<T> implements IPool,AutoCloseable {
    private static final int DEFAULTCAPACITY = 10;
    private static final int MAXCAPACITY = 100;
    private final Queue<T> objectQueue;
    private final Supplier<T> onCreate;
    private final Consumer<T> onGet;
    private final Consumer<T> onRelease;
    private final Consumer<T> onDelete;
    private final int maxCapacity;
    //現在返却されていないインスタンスの数。
    private int activeCount;

    /**
     * @param onCreate 生成関数 Class::newを推奨
     */
    public ObjectPool(final Supplier<T> onCreate) {
        this(onCreate, null, null, null, DEFAULTCAPACITY, MAXCAPACITY);
    }

    /**
     * @param onCreate        生成関数 Class::newを推奨
     * @param defaultCapacity 初期化時に生成しておく数 重いクラス、使いまわしが多い場合は作ることを推奨
     * @param maxCapacity     オブジェクトプールで管理する上限。超えた分は破棄されます。
     */
    public ObjectPool(final Supplier<T> onCreate, final int defaultCapacity, final int maxCapacity) {
        this(onCreate, null, null, null, defaultCapacity, maxCapacity);
    }

    /**
     * @param onCreate        生成関数 Class::newを推奨
     * @param onGet           オブジェクトプールから取り出すときに呼ばれる関数。生成された直後も呼ばれます。(再初期化関数を登録推奨)
     * @param onRelease       オブジェクトプールへ戻すときに呼ばれる関数。破棄されるかに関係なく呼ばれます。(非アクティブ化関数を登録推奨)。
     * @param onDelete        オブジェクトプールに戻せない場合、破棄される場合に呼ばれる関数。nullを代入したり、.close()よ読んだりしましょう。
     * @param defaultCapacity 初期化時に生成しておく数 重いクラス、使いまわしが多い場合は作ることを推奨
     * @param maxCapacity     オブジェクトプールで管理する上限。超えた分は破棄されます。
     */
    public ObjectPool(final Supplier<T> onCreate,final  Consumer<T> onGet,final Consumer<T> onRelease,final Consumer<T> onDelete,final int defaultCapacity,final int maxCapacity) {
        objectQueue = new ArrayDeque<>(maxCapacity);//最大容量確保
        this.onCreate = onCreate;
        this.onGet = onGet;
        this.onRelease = onRelease;
        this.onDelete = onDelete;
        this.maxCapacity = maxCapacity;
        //先に生成。
        int createCount = Math.min(defaultCapacity, maxCapacity);
        for (int i = 0; i < createCount; i++) {
            objectQueue.add(onCreate.get());
        }
    }


    /**
     * プールされているオブジェクトがある場合は取り出し、
     * ない場合は登録されたonCreate関数を呼び出します。
     * onGet関数がある場合は呼び出します。
     *
     * @return 登録された作成関数によって作られたTインスタンス。
     */
    @NonNull
    public T getOrCreate() {
        T instance;
        if (!objectQueue.isEmpty()) {
            instance = objectQueue.poll();
        } else {
            instance = onCreate.get();
        }
        if (onGet != null) {
            onGet.accept(instance);
        }
        activeCount++;
        assert instance != null;
        return instance;
    }

    /**
     * オブジェクトプールにインスタンスを返却します。
     * onReleaseが存在する場合は呼び出します。
     * キューで待機している容量が最大容量以上の場合はさらにonDelete関数を呼び出し、返却しません。
     *
     * @param instance 返却もしくは、破棄するインスタンス not null
     * @throws NullPointerException instanceがnullの場合
     */
    public void releaseOrDelete(@NonNull final T instance) {
        Objects.requireNonNull(instance);
        activeCount--;
        if (onRelease != null) {
            onRelease.accept(instance);
        }

        if (objectQueue.size() < maxCapacity) {
            objectQueue.add(instance);
        }
        else {
            if (onDelete != null) {
                onDelete.accept(instance);
            }
        }
    }

    /**
     * キューを空にします。
     */
    public void clearPool() {
        if (onDelete != null) {
            while (!objectQueue.isEmpty()) {
                T instance = objectQueue.poll();
                onDelete.accept(instance);
            }
        }
        objectQueue.clear();
        activeCount = 0;
    }

    @Override
    public void close() {
        clearPool();
    }
}
