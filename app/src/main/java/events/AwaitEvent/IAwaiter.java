package events.AwaitEvent;

/**
 * イベントを1回受信したら購読解除(使い切りタイプのイベント)
 */
public interface IAwaiter {
    public void start();
    public void cancel();
    public boolean isCompleted();
    public boolean isCancelled();
    public void reset();
}
