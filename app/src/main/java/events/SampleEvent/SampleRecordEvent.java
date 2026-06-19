package events.SampleEvent;

import android.util.Log;

import Utils.StringPool.StringBufferBuilderPool;

public record SampleRecordEvent(int number, String message) implements ISampleEvent {
    public static final String TAG = SampleRecordEvent.class.getSimpleName();
    @Override
    public void sayHello(){
        Log.d(TAG, StringBufferBuilderPool.Join("", message, ",", number));
    }
}
