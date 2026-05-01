package jp.ac.gifu_u.programmingjissen2;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import events.SampleEvent;
import events.SystemEventHub;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//        //描画処理を上書き
          setContentView(new ViewActivity(this));
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
//        //ボタンのイベントを受信するクラスをインスタンス化
//        ButtonActivity buttonActivity = new ButtonActivity(this);
//        //リソースから、buttonというIdのものを持って来てButtonクラスにキャスト
//        Button b = (Button)findViewById(R.id.button);
//        //リスナーを登録
//        b.setOnClickListener(buttonActivity);
        SystemEventHub.Subscribe(SampleEvent.class,this::EventListener);
    }
    private void EventListener(SampleEvent s){
        Toast t = Toast.makeText(
                this, s.message(), Toast.LENGTH_SHORT);
        t.show();
    }
}