package events.AwaitEvent;

import events.SystemEventHub;

/**
 * イベントを購読し、条件を満たせば自動で購読解除するクラス(使い切りイベント)
 * @param <TEvent> イベントの受信クラス。
 */
public abstract class EventAwaiter<TEvent> implements IAwaiter {

    //ここあとでEnum Stateにしたい。
    private boolean hasStarted = false;
    private boolean completed = false;
    private boolean cancelled = false;
    private boolean isReceiving = false;

    /**
     * 購読を開始します。
     * getEventType()がnullだと購読できません。
     * @throws IllegalStateException イベントが多重購読された場合。
     */
    @Override
    public void start() {
        if(hasStarted) {
            throw new IllegalStateException("イベントが多重登録されました。");
        }

        try{
            //receiveを購読
            //一応null入れなきゃエラーはでないけど、包んでおく。
            SystemEventHub.subscribe(
                    getEventType(),
                    this::receive);
            hasStarted = true;
        }
        catch(Exception e){
            hasStarted = false;
            throw e;
        }
    }

    /**
     * 購読を中止します。
     */
    @Override
    public void cancel() {
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
        isReceiving = true;
        boolean matched = false;

        try {
            //カスタムクラスでエラーが出る可能性をキャッチ
            matched = match(event);
            if (!matched) {
                return;
            }
            onReceive(event);

        }
        finally {
            //必ず戻す
            isReceiving = false;

            //onReceive()でエラッタ場合も呼ぶ
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

        completed = true;

        //購読解除
        SystemEventHub.unsubscribe(
                getEventType(),
                this::receive);

        try {
            onComplete();
        }
        finally {
            //ここでresetが呼ばれます。
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
     * @return TEvent.classを書いて
     */

    protected abstract Class<TEvent> getEventType();

    protected void onComplete() {

    }

    /**
     *内部状態をリセット
     */
    @Override
    public final void reset() {
        hasStarted = false;
        completed = false;
        cancelled = false;
        isReceiving = false;
        onReset();
    }
    protected void onReset(){

    }
}
