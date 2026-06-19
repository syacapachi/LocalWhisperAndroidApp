package events.AwaitEvent;

import android.util.Log;

import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;

/**
 * イベントを購読し、条件を満たせば自動で購読解除するクラス(使い切りイベント)
 * @param <TEvent> イベントの受信クラス。
 */
public abstract class EventAwaiter<TEvent> implements IAwaiter {
    private static final String TAG = EventAwaiter.class.getSimpleName();
    //ここあとでEnum Stateにしたい。
    private boolean hasStarted = false;
    private boolean completed = false;
    private boolean cancelled = false;
    private boolean isReceiving = false;

    //登録する関数は保持しないと、別インスタンスになる。
    //SystemEventHub.subscribe(
    //  getEventType(),
    //  this::receive//これはnew Consumer<TEvent>(this::receive);つまり、毎回新規作成される、
    //  );
    // -> unsubscribe失敗。
    private final Consumer<TEvent> onReceived = this::receive;

    /**
     * 購読を開始します。
     * @throws IllegalStateException イベントが多重購読された場合。getEventType()がnullの場合。
     */
    @Override
    public void start() {
        if(hasStarted) {
            DebugLog("イベントが多重登録されました。");
        }

        Class<TEvent> eventType = getEventType();
        if(eventType != null){
            //receiveを購読
            DebugLog("Start");
            SystemEventHub.subscribe(
                    eventType,
                    onReceived);
            hasStarted = true;
        }
        else{
            hasStarted = false;
            DebugLog("getEventType()がnullです。");
        }
    }

    /**
     * 購読を中止します。
     */
    @Override
    public void cancel() {
        DebugLog("Cancelled");
        cancelled = true;
        complete();
    }

    /**
     * 購読する関数です
     * @param event 受信イベント
     */
    private void receive(TEvent event) {
        //無限ループ防止
        if (isReceiving) {
            return;
        }
        DebugLog("Receive");
        isReceiving = true;
        boolean matched = false;

        try {
            //カスタム関数でエラーが出る可能性があるので
            matched = match(event);
            if (!matched) {
                return;
            }
            onReceive(event);
        }
        finally {
            //例外があっても必ず戻す
            isReceiving = false;

            //onReceive()でエラッた場合も呼ぶ
            if (matched) {
                complete();
            }
        }
    }

    /**
     * 購読解除の共通処理
     */
    protected final void complete() {

        if (completed) {
            return;
        }

        DebugLog("Completed");
        completed = true;

        //購読解除
        SystemEventHub.unsubscribe(
                getEventType(),
                onReceived);

        try {
            onComplete();
        }
        finally {
            //ここでresetが呼ばれる。
            AwaiterHub.release(this);
        }
    }

    /**
     * @return イベント解除済みかどうか
     */
    @Override
    public final boolean isCompleted(){
        return completed;
    }
    @Override
    public final boolean isCancelled(){
        return cancelled;
    }

    /**
     * @param event 受信イベント
     * @return 待っているイベントかの判別
     */
    protected abstract boolean match(TEvent event);

    /**
     * 待っていたイベントの場合
     * @param event 受信イベント
     */

    protected abstract void onReceive(TEvent event);

    /**
     *
     * @return TEvent.classを書く
     */

    protected abstract Class<TEvent> getEventType();

    protected void onComplete() {

    }

    /**
     * AwaiterHub.release()で呼ばれます。
     * 内部状態をリセット
     */
    @Override
    public final void reset() {
        DebugLog("Reset");
        hasStarted = false;
        completed = false;
        cancelled = false;
        isReceiving = false;
        onReset();
    }
    protected void onReset(){

    }

    /**
     * デバック用のログ出力
     * @param message
     */
    protected final void DebugLog(String message){
        Log.d(TAG, StringBufferBuilderPool.Join("", message, ": ", getClass().getSimpleName()));
    }

    /**
     * 値付きのデバック用ログを pooled builder で組み立てて出力します。
     *
     * @param message ログの先頭メッセージ
     * @param value 追加で表示する値
     */
    protected final void DebugLog(String message, Object value){
        Log.d(TAG, StringBufferBuilderPool.Join(
                "",
                message,
                value,
                ": ",
                getClass().getSimpleName()
        ));
    }
}
