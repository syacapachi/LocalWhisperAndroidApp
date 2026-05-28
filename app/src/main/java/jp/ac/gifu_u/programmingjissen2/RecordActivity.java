package jp.ac.gifu_u.programmingjissen2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import events.AwaitEvent.AwaiterHub;
import events.Request.PermissionAwaiter;

public class RecordActivity implements Runnable {
    private final static String TAG = RecordActivity.class.getSimpleName();
    private final Activity activity;
    private final Button recordButton;
    private final TextView resultTextView;
    static final int FREQUENCY = 8000;
    static final int REQUESTCODE = 2000;
    AudioRecord rec;
    short[] buf;
    //録音スレッドの状態監視
    //volatileで最適化阻止(マルチスレッド用)
    volatile boolean isRecording;
    Thread recordThread;
    public RecordActivity(Activity activity, Button button, TextView resultTextView){
        this.activity = activity;
        this.recordButton = button;
        this.resultTextView = resultTextView;
        recordButton.setText("録音");
        recordButton.setOnClickListener((view)->{
            if(!isRecording){
                if(StartRecord())
                    recordButton.setText("停止");
            }
            else{
                if(StopRecord())
                    recordButton.setText("録音");
            }
        });
    }

    /**
     * 許可がない場合
     * 録音の許可を求めます。
     * また、録音スレッドを立ち上げて録音を開始します。
     * @return 録音開始できたか
     */
    public boolean StartRecord(){
        int bufferSize = AudioRecord.getMinBufferSize(
                FREQUENCY,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        //許可が必要<uses-permission android:name="android.permission.RECORD_AUDIO" />
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            PermissionAwaiter awaiter = AwaiterHub.rentAwaiter(PermissionAwaiter.class);
            awaiter.initialize(
                    REQUESTCODE,
                    (result) ->{
                        Log.d(TAG, "Record Permission: " + result);
                        if(result) {
                            rec = new AudioRecord(
                                    MediaRecorder.AudioSource.MIC,
                                    FREQUENCY,
                                    AudioFormat.CHANNEL_IN_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT,
                                    bufferSize
                            );
                            buf = new short[bufferSize];
                            if(isRecording){
                                StopRecord();
                            }
                            isRecording = true;
                            recordThread = new Thread(this);
                            recordThread.start();
                        }
                    }
            );

            //待機開始
            awaiter.start();

            //リクエスト送信
            ActivityCompat.requestPermissions(
                    activity,//結果を通知するActivity
                    //求める権限
                    new String[]{Manifest.permission.RECORD_AUDIO}
                    //結果で使うリクエスト識別コード(かぶらないように)
                    ,REQUESTCODE);
            return false;
        }
        rec = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                FREQUENCY,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );
        buf = new short[bufferSize];
        if(isRecording){
            StopRecord();
        }
        isRecording = true;
        recordThread = new Thread(this);
        recordThread.start();
        return true;
    }
    /**
     * 録音が開始されている場合、止めます。
     */
    public boolean StopRecord(){
        if(!isRecording)  return false;
        isRecording = false;

        if(rec != null){
            //stop()を呼ぶとread()が止まる。
            rec.stop();
        }

        if(recordThread != null){
            try{
                recordThread.join();
            }
            catch (InterruptedException e){
                Thread.currentThread().interrupt();
                return false;
            }
        }
        recordThread = null;
        return true;
    }

    //録音スレッドでの関数
    //録音されたデータの最大振幅をトーストで表示
    @Override
    public void run() {
        if(rec == null) return;
        //録音開始
        rec.startRecording();
        try {
            while (isRecording) {
                //この処理がスレッドを止める。のでマルチスレッドにする。
                int datasize = rec.read(buf, 0, buf.length), max = 0;
                //録音されたデータの最大振幅を保存。
                for (int i = 0; i < datasize; i++) {
                    if ((buf[i] > 0) && (buf[i] > max)) {
                        max = buf[i];
                    }
                    if ((buf[i] < 0) && (buf[i] < -max)) {
                        max = -buf[i];
                    }
                }
                //メインスレッドに返す(UIはメインスレッドのみ)。
                String str = Integer.toString(max);
                activity.runOnUiThread(
                        () -> {
                            resultTextView.setText(str);
                        });
            }
        }
        finally {
            //必ず開放
            rec.release();
            rec = null;
        }
    }
}
