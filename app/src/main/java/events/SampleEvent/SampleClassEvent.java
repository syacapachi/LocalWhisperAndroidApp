package events.SampleEvent;

import android.util.Log;

public class SampleClassEvent implements ISampleEvent{
    private static final String TAG = SampleClassEvent.class.getSimpleName();
    protected final String msg;
    public SampleClassEvent(String message){
        this.msg = message;
    }
    @Override
    public void sayHello(){
        Log.d(TAG,msg);
    }
}
