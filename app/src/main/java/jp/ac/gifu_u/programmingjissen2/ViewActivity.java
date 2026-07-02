package jp.ac.gifu_u.programmingjissen2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
/**
 * 画面の制御クラス Viewを継承したクラス
*/
public class ViewActivity  extends View {
    private static final String TAG = ViewActivity.class.getSimpleName();
    // イベント発生時の X 座標、Y 座標を保存するための動的配列
    private final ArrayList<Integer> array_x;
    private final ArrayList<Integer> array_y;
    private final ArrayList<Boolean> array_status;

    public ViewActivity(Context context) {
        super(context);
        array_x = new ArrayList<>();
        array_y = new ArrayList<>();
        array_status = new ArrayList<>();
    }

    /**
     * 描画開始処理
     * @param canvas the canvas on which the background will be drawn
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // どのように描画するかを指定する Paint オブジェクトを作成
        //背景を白で塗りつぶす
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        canvas.drawRect(new Rect(0, 0,
                canvas.getWidth(), canvas.getHeight()), p);

        // 描画用の Paint オブジェクトを用意
        p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.RED);
        // 配列内の座標を読み出して線（軌跡）を描画
        for (int i = 1; i < array_status.size(); i++) {
        // 描画するように(true)状態値が与えられているとき
        // 一度離してしてから次に押されるまでの移動分は描画しない
            if ((Boolean) array_status.get(i)) {
        // 開始点の終了点の座標の値を取得
                int x1 = (Integer) array_x.get(i - 1);
                int x2 = (Integer) array_x.get(i);
                int y1 = (Integer) array_y.get(i - 1);
                int y2 = (Integer) array_y.get(i);
        // 線を描画
                canvas.drawLine(x1, y1, x2, y2, p);
            }
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.logo);

        canvas.drawBitmap(bitmap, 0, 10, p);
    }

    /**
     * タッチパネルを操作した時に呼ばれるメソッド
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // 座標を取得
        int x = (int) event.getX();
        int y = (int) event.getY();
        // イベントに応じて動作を変更
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: // タッチパネルが押されたとき
            case MotionEvent.ACTION_POINTER_DOWN:
                array_x.add(x); // 座標を配列に保存
                array_y.add(y); // 線の描画はしない(false)
                array_status.add(Boolean.FALSE);
                invalidate(); // 画面を強制的に再描画
                break;
            case MotionEvent.ACTION_MOVE:
                array_x.add(x); // 座標を配列に保存
                array_y.add(y); // 線の描画をする(true)
                array_status.add(Boolean.TRUE);
                invalidate(); // 画面を強制的に再描画
                break;
            case MotionEvent.ACTION_UP: // タッチパネルから離れたとき
            case MotionEvent.ACTION_POINTER_UP:
                array_x.add(x); // 座標を配列に保存
                array_y.add(y); // 線の描画をする(true)
                array_status.add(Boolean.TRUE);
                invalidate(); // 画面を強制的に再描画
                break;
        }
        return true;
    }
}
