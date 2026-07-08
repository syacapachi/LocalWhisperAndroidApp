package jp.ac.gifu_u.programmingjissen2;
import android.hardware.Sensor;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.function.Consumer;

import jp.ac.gifu_u.programmingjissen2.Record.RecordActivity;
import jp.ac.gifu_u.programmingjissen2.ResultUI.TranscriptionListActivity;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;

import events.AwaitEvent.AwaiterHub;
import events.Request.RequestPermissionResultEvent;
import events.SampleEvent.SampleRecordEvent;
import events.SystemEventHub;
import jp.ac.gifu_u.programmingjissen2.Transcription.BackgroundWhisperService;

/// アプリの状態を監視するクラス

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private SensorActivity sensorActivity;
    private RecordActivity recordActivity;
    private ActivityResultLauncher<String[]> audioFilePicker;

    Consumer<SampleRecordEvent> eventListener1 = this::EventListener;
    Consumer<SampleRecordEvent> eventListener2 = this::EventListener2;

    //アプリ起動時に呼ばれる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//      EdgeToEdge.enable(this);
        audioFilePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onAudioFileSelected
        );
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
        Button whisperSettingsButton = (Button) findViewById(R.id.whisperSettingsButton);
        RadioGroup whisperModelRadioGroup = findViewById(R.id.whisperModelRadioGroup);
        TextView recordText = findViewById(R.id.recordText);
        TextView whisperStatusText = findViewById(R.id.whisperStatusText);
        TextView whisperBenchmarkText = findViewById(R.id.whisperBenchmarkText);
        Button transcriptionHistoryButton = findViewById(R.id.transcriptionHistoryButton);
        Button audioFileTranscriptionButton = findViewById(R.id.audioFileTranscriptionButton);
        transcriptionHistoryButton.setOnClickListener((view) -> startActivity(
                new Intent(this, TranscriptionListActivity.class)
        ));
        audioFileTranscriptionButton.setOnClickListener((view) -> audioFilePicker.launch(
                new String[]{"audio/*", "video/*", "application/octet-stream"}
        ));
        recordActivity = new RecordActivity(this, new WhisperRecordControls(
                recordButton,
                whisperSettingsButton,
                whisperModelRadioGroup,
                recordText,
                whisperStatusText,
                whisperBenchmarkText
        ));

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
        //sensorActivity.AddSensorListener(Sensor.TYPE_MAGNETIC_FIELD);
        sensorActivity.requestLocationUpdate(1000,10);
        if (recordActivity != null) {
            recordActivity.RefreshSettings();
        }
    }
    //ホーム画面の繊維など、アプリがメインではなくなったときに呼ばれる
    //再開時はonResume()
    @Override
    protected void onPause(){
        super.onPause();
        Log.d(TAG, "onPause");
        //電池を消耗しないようにセンサーだけ止める。録音は foreground service 側で継続する。
        sensorActivity.RemoveListener();
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
        if (recordActivity != null) {
            recordActivity.Dispose();
        }
        if (!BackgroundWhisperService.isServiceActive()) {
            // 待機しているイベント解除
            AwaiterHub.clear();
            // 全てのイベント購読を解除
            SystemEventHub.clear();
        }

        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
    private void EventListener(@NonNull SampleRecordEvent s){
        Toast t = Toast.makeText(
                this, s.message(), Toast.LENGTH_SHORT);
        t.show();
    }
    private void EventListener2(@NonNull SampleRecordEvent s){
        Log.d(TAG,s.message());
    }

    /**
     * 音声ファイル選択結果を録音画面コントローラへ渡します。
     *
     * @param uri 選択されたファイル URI。例: {@code content://media/external/audio/media/1}
     */
    private void onAudioFileSelected(final Uri uri) {
        if (uri == null || recordActivity == null) {
            return;
        }

        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException ignored) {
            // 一時許可だけで読める provider もあるため、永続化失敗は処理継続します。
        }
        recordActivity.TranscribeAudioFile(uri);
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

