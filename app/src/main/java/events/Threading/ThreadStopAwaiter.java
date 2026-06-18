package events.Threading;

import android.util.Log;

import java.util.Objects;
import java.util.function.Consumer;

import events.AwaitEvent.EventAwaiter;

/**
 * 指定した threadId の {@link ThreadStoppedEvent} を 1 回だけ待つ Awaiter です。
 */
public class ThreadStopAwaiter extends EventAwaiter<ThreadStoppedEvent>{
    /** ログ出力用タグです。 */
    private static final String TAG = ThreadStopAwaiter.class.getSimpleName();

    /** 待ち受け対象のスレッド停止イベント ID です。 */
    private String threadId;

    /** 対象イベントを受け取ったときに呼ぶ callback です。 */
    private Consumer<ThreadStoppedEvent> callback;

    /**
     * 待ち受ける threadId と callback を設定します。
     *
     * @param threadId 待ち受け対象の停止イベント ID
     * @param callback イベント受信時の処理
     */
    public void initialize(
            String threadId,
            Consumer<ThreadStoppedEvent> callback
    ) {
        this.threadId = threadId;
        this.callback = callback;
    }

    /**
     * 受信イベントが待ち受け対象の threadId か判定します。
     */
    @Override
    protected boolean match(ThreadStoppedEvent event) {
        return Objects.equals(threadId, event.threadId());
    }

    /**
     * 対象スレッドの停止イベントを callback へ渡します。
     */
    @Override
    protected void onReceive(ThreadStoppedEvent event) {
        if (callback == null) {
            return;
        }

        try {
            callback.accept(event);
        } catch (Exception e) {
            Log.e(TAG, "Thread stop callback error", e);
        }
    }

    /**
     * この Awaiter が購読するイベント型を返します。
     */
    @Override
    protected Class<ThreadStoppedEvent> getEventType() {
        return ThreadStoppedEvent.class;
    }

    /**
     * ObjectPool 返却時に保持している threadId と callback を消します。
     */
    @Override
    public void onReset() {
        threadId = null;
        callback = null;
    }
}
