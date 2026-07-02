package events;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;

/**
 * イベント駆動型設計をするために、設置
 * Subscribeで型と関数を登録、Unsubscribeで、解除
 * Publishでイベント発火(親クラスや、インターフェースにも通知が届く)
*/
 public final class SystemEventHub {
    private final static String TAG = SystemEventHub.class.getSimpleName();
    /**クラスと、関数のリスト(スレッドセーフ)をもつマップ(スレッドセーフ)*/
    private static final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> events = new ConcurrentHashMap<>();
    /**クラスと、そのクラスを含む基底クラスのキャッシュ(スレッドセーフ)*/
    private static final Map<Class<?>,CopyOnWriteArrayList<Class<?>>> typeCache = new ConcurrentHashMap<>();
    private SystemEventHub(){}
    /**
     * 既に購読しているクラスにイベントを発行します。
     * @param event 購読者に送るイベントの構造体 ,not null
     * @param <T> 発行するイベントクラス。イベントごとに作ったいほうがいいです。not null
     * @exception NullPointerException eventがnullの場合投げられます。
     * @apiNote オーバーロード情報invokeSuperClass = true;(基底クラスの購読者にも通知される)
     */
    public static <T> void publish(@NonNull final T event){
        publish(event,true);
    }
    /**
     * 既に購読しているクラスにイベントを発行します。
     * @param event 購読者に送るイベントの構造体 ,not null
     * @param invokeSuperClass 基底イベントクラスを継承している場合、基底クラスを購読しているクラスにも発行するか
     * @param <T> 発行するイベントクラス。イベントごとに作ったいほうがいいです。not null
     * @exception NullPointerException eventがnullの場合投げられます。
     */
    public static <T> void publish(@NonNull final T event, final boolean invokeSuperClass){
        Objects.requireNonNull(event);
        Class<?> eventClass = event.getClass();
        if(invokeSuperClass) {
            //C#のTryGetの感じ。Contains()を使うより効率がいい
            CopyOnWriteArrayList<Class<?>> classes =
                    //なかったら作る。
                    typeCache.computeIfAbsent(eventClass, cls -> {
                        CopyOnWriteArrayList<Class<?>> list =
                                new CopyOnWriteArrayList<>();
                        //作るときに、いまの初期値入れる。
                        for (var entry : events.entrySet()) {
                            if (entry.getKey().isAssignableFrom(cls)) {
                                list.add(entry.getKey());
                            }
                        }
                        return list;
                    });
            for (Class<?> clazz: classes) {
                var consumers = events.get(clazz);
                if(consumers == null)
                    continue;
                for (Consumer<?> consumer : consumers) {
                    @SuppressWarnings("unchecked")
                    Consumer<T> action = (Consumer<T>) consumer;
                    try{
                        action.accept(event);
                    }
                    catch(Exception e){
                        Log.e(TAG, StringBufferBuilderPool.Join(
                                "",
                                "Event Error publish Class : ",
                                eventClass.getSimpleName()
                        ), e);
                    }
                }
            }
        }
        else {
            var consumers = events.get(eventClass);
            if (consumers == null) return;
            for (Consumer<?> consumer : consumers) {
                @SuppressWarnings("unchecked")
                Consumer<T> action = (Consumer<T>) consumer;
                try {
                    action.accept(event);
                } catch (Exception e) {
                    Log.e(TAG, StringBufferBuilderPool.Join(
                            "",
                            "Event Error publish Class : ",
                            eventClass.getSimpleName()
                    ), e);
                }
            }
        }
    }

    /**
     * イベントの購読
     * @param type 購読するイベントクラスのType,not null
     * @param action イベント発行時に実行する関数。not null
     * @param <T> イベントクラスならなんでもnot null
     * @exception NullPointerException 引数にnullを含めた場合投げられます。
     * @apiNote   関数を複数回を購読した場合、その回数だけ実行されます。
     * また、C#と違い、Javaでは this::funcを入れた場合、Consumer<T>のインスタンスが新規で作られるので
     * final Consumer<T> func = this::func;のように持っておかないと購読解除できません。
     */
    public static <T> void subscribe(final Class<T> type, final Consumer<T> action){
        //nullだったらNullPointerExceptionを投げる
        Objects.requireNonNull(action);
        Objects.requireNonNull(type);
        Log.d(TAG, StringBufferBuilderPool.Join("", "subscribe: ", action));
        events.computeIfAbsent(
                type,
                (v)-> {
                    //古いキャッシュを消去する。
                    typeCache.clear();
                    return new CopyOnWriteArrayList<>();
                }).add(action);
    }
    /**
     * イベントの購読解除
     * @param type 購読解除するイベントクラスのType,not null
     * @param action 購読解除する関数。not null
     * @param <T> イベントクラスならなんでもnot null
     * @apiNote 登録されてない関数が入った場合は何も起きません。
     * また、C#と違い、Javaでは this::funcを入れた場合、Consumer<T>のインスタンスが新規で作られるので
     * final Consumer<T> func = this::func;のように持っておかないと購読解除できません。
     */
    public static <T> void unsubscribe(final Class<T> type, final Consumer<T> action) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(action);
        List<Consumer<?>> list = events.get(type);
        if (list == null) return;
        Log.d(TAG, StringBufferBuilderPool.Join("", "unsubscribe: ", action));
        if (!list.remove(action)){
            Log.w(TAG, StringBufferBuilderPool.Join("", action, "can not unsubscribe"));
        }

        if (list.isEmpty()) {
            events.remove(type);
            typeCache.clear();
        }
    }

    /**
     * 全てのイベントの購読解除
     */
    public static void clear(){
        events.clear();
        typeCache.clear();
    }
}
