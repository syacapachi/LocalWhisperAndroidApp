package events.SampleEvent;

import android.util.Log;

public record SampleEvent(int number, String message) implements ISampleEvent {
    public static final String TAG = SampleEvent.class.getSimpleName();
    @Override
    public void sayHello(){
        Log.d(TAG,message + number);
    }
}
