package Utils.Pool;

public interface IPoolable {
    public void onCreate();
    public void onGet();
    public void onRelease();
    public void onDelete();
}
