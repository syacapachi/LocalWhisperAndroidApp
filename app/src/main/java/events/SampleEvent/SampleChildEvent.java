package events.SampleEvent;

import android.nfc.Tag;
import android.util.Log;

public class SampleChildEvent extends SampleClassEvent{

    private static final String TAG= SampleChildEvent.class.getSimpleName();
    protected final int number;
    public SampleChildEvent(String message, int number){
        super(message);
        this.number = number;
    }
    @Override
    public void sayHello(){
        super.sayHello();
        Log.d(TAG,Integer.toString(number));
    }
}
