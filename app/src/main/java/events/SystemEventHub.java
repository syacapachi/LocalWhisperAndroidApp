package events;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// イベント駆動型設計をするために、設置
/// Subscribeで型と関数を登録、Unsubscribeで、解除
/// Publishでイベント発火(親クラスや、インターフェースにも通知が届く)
public class SystemEventHub {
    private static final Map<Class<?>, List<Consumer<?>>> events = new HashMap<>();
    /// invokeSuperClass = true
    public static <T> void Publish(T event){
        Publish(event,true);
    }
    public static <T> void Publish(T event, boolean invokeSuperClass){
        if(invokeSuperClass) {
            for (Map.Entry<Class<?>, List<Consumer<?>>> entry : events.entrySet()) {
                if (entry.getKey().isAssignableFrom(event.getClass())) {
                    for (Consumer<?> consumer : entry.getValue()) {
                        @SuppressWarnings("unchecked")
                        Consumer<T> action = (Consumer<T>) consumer;
                        action.accept(event);
                    }
                }
            }
        }
        else{
            if(events.containsKey(event.getClass())){
                for(Consumer<?> consumer: Objects.requireNonNull(events.get(event.getClass()))) {
                    @SuppressWarnings("unchecked")
                    Consumer<T> action = (Consumer<T>) consumer;
                    action.accept(event);
                }
            }
        }
    }
    public static <T> void Subscribe(Class<T> type, Consumer<T> action){
        events.computeIfAbsent(type,(v)-> new ArrayList<>()).add(action);
    }
    public static <T> void Unsubscribe(Class<T> type, Consumer<T> action){
        List<Consumer<?>> list = events.get(type);
        if (list != null) {
            list.remove(action);
        }
    }
}
