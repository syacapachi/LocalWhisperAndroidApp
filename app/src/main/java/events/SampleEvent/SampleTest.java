package events.SampleEvent;

import android.util.Log;

import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;
import events.SystemEventHub;

public class SampleTest {
    static final String TAG = SampleTest.class.getSimpleName();
    static Consumer<ISampleEvent> sampleEventListener = SampleTest::SampleEventListener;
    public static void CheckTest(){
        SystemEventHub.subscribe(ISampleEvent.class,sampleEventListener);
        SystemEventHub.publish(new SampleRecordEvent(10,"Invoke SampleRecordEvent"));
        SystemEventHub.publish(new SampleClassEvent("Invoke SampleClassEvent"));
        SystemEventHub.publish(new SampleChildEvent("Invoke SampleChildEvent",100));
        SystemEventHub.unsubscribe(ISampleEvent.class,sampleEventListener);
    }
    private static void SampleEventListener(ISampleEvent sampleEvent){
        Log.d(TAG, StringBufferBuilderPool.Join(
                "",
                "Event Received:",
                sampleEvent.getClass()
        ));
        sampleEvent.sayHello();
    }
}
