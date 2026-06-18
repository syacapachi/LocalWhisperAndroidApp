package jp.ac.gifu_u.programmingjissen2;
import android.hardware.Sensor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.function.Consumer;

import events.Request.RequestPermissionResultEvent;
import events.SampleEvent.SampleRecordEvent;
import events.SampleEvent.SampleTest;
import events.SystemEventHub;

/// アプリの状態を監視するクラス

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private SensorActivity sensorActivity;
    private RecordActivity recordActivity;

    Consumer<SampleRecordEvent> eventListener1 = this::EventListener;
    Consumer<SampleRecordEvent> eventListener2 = this::EventListener2;

    //アプリ起動時に呼ばれる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//      EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        //ログの書き方,Tagはクラス名が多い
        Log.d(TAG, "onCreate");
//        //描画処理を上書き
//          setContentView(new ViewActivity(this));
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
        //ボタンのイベントを受信するクラスをインスタンス化
        ButtonActivity finishButton = new ButtonActivity(
                this,
                (view) -> {
                    Toast t = Toast.makeText(
                            this, "Finish", Toast.LENGTH_SHORT);
                    t.show();
                    SystemEventHub.publish(new SampleRecordEvent(1, "Invoked!"));
                    Log.d(TAG, "Finish");
                    //アプリを終了する
                    this.finish();
                });
        //リソースから、buttonというIdのものを持って来てButtonクラスにキャスト
        Button b = (Button) findViewById(R.id.button);
        //リスナーを登録
        b.setOnClickListener(finishButton);

        //イベントリスナー購読
        SystemEventHub.subscribe(SampleRecordEvent.class, eventListener1);
        SystemEventHub.subscribe(SampleRecordEvent.class, eventListener2);

        //センサーのリスナーのインスタンスを作成
        sensorActivity = new SensorActivity(this);

        //録音のインスタンスを作成。
        Button recordButton = (Button) findViewById(R.id.recordButton);
        TextView recordText = findViewById(R.id.recordText);
        recordActivity = new RecordActivity(this, recordButton, recordText);

        //イベントが親クラスに行くかの確認。
        //SampleTest.CheckTest();
    }
    //画面が見えるタイミングで呼ばれる
    @Override
    protected void onStart(){
        super.onStart();
        Log.d(TAG, "onStart");
    }
    //画面が描画開始タイミングで呼ばれる
    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG, "onResume");
        sensorActivity.AddSensorListener(Sensor.TYPE_LIGHT);
        sensorActivity.AddSensorListener(Sensor.TYPE_MAGNETIC_FIELD);
        sensorActivity.requestLocationUpdate(1000,10);
    }
    //ホーム画面の繊維など、アプリがメインではなくなったときに呼ばれる
    //再開時はonResume()
    @Override
    protected void onPause(){
        super.onPause();
        Log.d(TAG, "onPause");
        //電池を消耗しないようにすぐ消す。
        sensorActivity.RemoveListener();
        recordActivity.StopRecord();
    }
    //Pauseから時間がたつと呼ばれる。バックグラウンドで動いている。
    //ここからの再開はReStart()
    @Override
    protected void onStop(){
        super.onStop();
        Log.d(TAG, "onStop");
    }
    //onStop()から再開した場合
    //この後、onStart()が呼ばれる
    @Override
    protected void onRestart(){
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
    //アプリ切り替え時、長時間放置などアプリがが廃棄されるタイミング。
    @Override
    protected void onDestroy(){
        // イベント購読を解除
        SystemEventHub.clear();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
    private void EventListener(SampleRecordEvent s){
        Toast t = Toast.makeText(
                this, s.message(), Toast.LENGTH_SHORT);
        t.show();
    }
    private void EventListener2(SampleRecordEvent s){
        Log.d(TAG,s.message());
    }

    /**
     * ユーザーの許可の結果を貰えた時に呼ばれる。
     * @param requestCode The request code passed in {@link #requestPermissions}.
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either
     *                     {@link android.content.pm.PackageManager#PERMISSION_GRANTED} or
     *                     {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     *
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //イベントとして投げる
        SystemEventHub.publish(new RequestPermissionResultEvent(requestCode,permissions,grantResults));

    }
}