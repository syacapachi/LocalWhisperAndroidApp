package events.Request;

import android.content.pm.PackageManager;
import android.util.Log;

import java.util.function.Consumer;

import events.AwaitEvent.EventAwaiter;

/**
 * ユーザーに許可を求めた結果を待つイベント
 */
public class PermissionAwaiter extends EventAwaiter<RequestPermissionResultEvent> {

    private static final String TAG = PermissionAwaiter.class.getSimpleName();
    private int requestCode;
    private Consumer<Boolean> callback;

    public void initialize(
            int requestCode,
            Consumer<Boolean> callback)
    {
        this.requestCode = requestCode;
        this.callback = callback;
    }

    @Override
    protected boolean match(RequestPermissionResultEvent event) {
        return event.requestCode() == requestCode;
    }

    @Override
    protected void onReceive(RequestPermissionResultEvent event) {

        boolean granted = false;

        for (int result : event.grantResults()) {

            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }

        try {
            callback.accept(granted);
        }
        catch (Exception e){
            Log.e(TAG,"request code:"+requestCode+" callback occur error ",e);
        }
    }

    @Override
    protected Class<RequestPermissionResultEvent> getEventType() {
        return RequestPermissionResultEvent.class;
    }

    @Override
    public void onReset() {
        callback = null;
    }
}