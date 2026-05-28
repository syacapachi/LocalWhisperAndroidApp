package events.SampleEvent;

import android.util.Log;

public record SampleRecordEvent(int number, String message) implements ISampleEvent {
    public static final String TAG = SampleRecordEvent.class.getSimpleName();
    @Override
    public void sayHello(){
        Log.d(TAG,message+"," + number);
    }
}
