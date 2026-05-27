package jp.ac.gifu_u.programmingjissen2;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import java.util.function.Consumer;

/**
 * ボタンのクリックを監視するクラス
 * レコードクラス(初期値を変更しないクラス、getterとかを自動で作ってくれるから便利)
 */
public record ButtonActivity(Activity activity,
                             Consumer<View> callback) implements View.OnClickListener {
    private final static String TAG = SensorActivity.class.getSimpleName();

    /**
     * クリックしたとき呼ばれる関数
     * Callbackを実行。
     * よくよく見たら、b.setOnClickListener((view) ->{});を遠回りしてるだけやんけ
     * 一応try-catch してるだけ存在意義あるか...
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        try {
            callback.accept(v);
        } catch (Exception e) {
            Log.e(TAG, "Button Callback occur error", e);
        }
    }
}
