package jp.ac.gifu_u.programmingjissen2;
import android.hardware.Sensor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import events.SampleEvent;
import events.SystemEventHub;

/// アプリの状態を監視するクラス

public class MainActivity extends AppCompatActivity {

    private SensorActivity sensorActivity;
    //アプリ起動時に呼ばれる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
//        //描画処理を上書き
//          setContentView(new ViewActivity(this));
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
        //ボタンのイベントを受信するクラスをインスタンス化
        ButtonActivity buttonActivity = new ButtonActivity(this);
        //リソースから、buttonというIdのものを持って来てButtonクラスにキャスト
        Button b = (Button)findViewById(R.id.button);
        //リスナーを登録
        b.setOnClickListener(buttonActivity);
        SystemEventHub.Subscribe(SampleEvent.class,this::EventListener);

        //センサーのリスナーのインスタンスを作成
        sensorActivity = new SensorActivity(this);
    }
    //画面が見えるタイミングで呼ばれる
    @Override
    protected void onStart(){
        super.onStart();
    }
    //画面が描画開始タイミングで呼ばれる
    @Override
    protected void onResume(){
        super.onResume();
        sensorActivity.AddSensorListener(Sensor.TYPE_LIGHT);
        sensorActivity.AddSensorListener(Sensor.TYPE_MAGNETIC_FIELD);
        sensorActivity.requestLocationUpdate(1000,10);
    }
    //ホーム画面の繊維など、アプリがメインではなくなったときに呼ばれる
    //再開時はonResume()
    @Override
    protected void onPause(){
        super.onPause();
        //電池を消耗しないようにすぐ消す。
        sensorActivity.RemoveListener();
    }
    //Pauseから時間がたつと呼ばれる。バックグラウンドで動いている。
    //ここからの再開はReStart()
    @Override
    protected void onStop(){
        super.onStop();
    }
    //onStop()から再開した場合
    //この後、onStart()が呼ばれる
    @Override
    protected void onRestart(){
        super.onRestart();
    }
    //アプリ切り替え時、長時間放置などアプリがが廃棄されるタイミング。
    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
    private void EventListener(SampleEvent s){
        Toast t = Toast.makeText(
                this, s.message(), Toast.LENGTH_SHORT);
        t.show();
    }
}