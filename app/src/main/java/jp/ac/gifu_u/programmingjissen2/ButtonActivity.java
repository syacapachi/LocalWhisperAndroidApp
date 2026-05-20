package jp.ac.gifu_u.programmingjissen2;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import events.*;
/**
 * ボタンのクリックを監視するクラス
 レコードクラス(初期値を変更しないクラス、getterとかを自動で作ってくれるから便利)
 */
public record ButtonActivity(Activity activity) implements View.OnClickListener {
    private final static String TAG = SensorActivity.class.getSimpleName();

    /**
     * クリックしたとき呼ばれる関数
     * アプリを終了する。
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        showToast("Finish");
        Log.d(TAG, "Finish");
        InvokeEvent();
        //アプリを終了する
        activity.finish();
    }

    public void showToast(String string) {
        Toast t = Toast.makeText(
                activity, string, Toast.LENGTH_SHORT);
        t.show();
    }
    public void InvokeEvent(){
        SystemEventHub.publish(new SampleEvent(1,"Invoked!"));
    }

}
