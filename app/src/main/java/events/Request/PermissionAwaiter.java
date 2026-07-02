package events.Request;

import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;
import events.AwaitEvent.EventAwaiter;

/**
 * ユーザーに許可を求めた結果を待つイベント
 */
public class PermissionAwaiter extends EventAwaiter<RequestPermissionResultEvent> {

    private static final String TAG = PermissionAwaiter.class.getSimpleName();
    private int requestCode;
    private Consumer<Boolean> callback;

    public void initialize(
            final int requestCode,
            final Consumer<Boolean> callback)
    {
        DebugLog("Initialized: ", requestCode);
        this.requestCode = requestCode;
        this.callback = callback;
    }

    @Override
    protected boolean match(@NonNull final RequestPermissionResultEvent event) {
        return event.requestCode() == requestCode;
    }

    @Override
    protected void onReceive(@NonNull final RequestPermissionResultEvent event) {
        DebugLog("onReceived: ", event.requestCode());

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
            Log.e(TAG, StringBufferBuilderPool.Join(
                    "",
                    "request code:",
                    requestCode,
                    " callback occur error "
            ), e);
        }
    }
    @Override
    protected void onComplete(){
        DebugLog("onComplete");
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
