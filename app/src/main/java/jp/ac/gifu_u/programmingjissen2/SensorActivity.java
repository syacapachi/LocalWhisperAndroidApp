package jp.ac.gifu_u.programmingjissen2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.List;

/// センサー(権限が必要ない)を監視するクラス
public class SensorActivity implements SensorEventListener, LocationListener {
    private final Activity activity;
    //センサー
    private final SensorManager sensorManager;
    //位置情報
    /// AndroidManifest.xmlの
    /// uses-permission タグで次のふたつを設定
    /// ⚫ Wifi などから大まかな位置情報を取得する ACCESS_COARSE_LOCATION
    /// ⚫ GPS などにより詳細な位置情報を取得する ACCESS_FINE_LOCATION
    private final LocationManager localeManager;
    public  SensorActivity(Activity activity){
        this.activity = activity;
        //マネージャークラス取得
        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        localeManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }
    public void AddSensorListener(int sensorType){
        // 明るさセンサ(TYPE_LIGHT)のリストを取得
        List<Sensor> sensors =
                sensorManager.getSensorList(sensorType);
        if(sensors.isEmpty()) return;
        //最初の1つを登録
        sensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_NORMAL);
    }
    public void requestLocationUpdate(long msvc, float meter){
        if (ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        //GPSの更新リクエストを更新
        localeManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                msvc,
                meter,
                this);
    }
    public void RemoveListener(){
        sensorManager.unregisterListener(this);
        localeManager.removeUpdates(this);
    }

    //センサーの精度が変わったとき
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //センサーの値が更新された時
    @Override
    public void onSensorChanged(SensorEvent event) {
        // 明るさセンサが変化したとき
        int sensorType = event.sensor.getType();
        String str = "";
        if (sensorType == Sensor.TYPE_LIGHT) {
            // 明るさの値（単位ルクス）を取得
            float intensity = event.values[0];

            str += Float.toString(intensity) + "ルクス\n";
        }
        else if(sensorType == Sensor.TYPE_MAGNETIC_FIELD){
            float intensity = event.values[0];
            // 結果をテキストとして表示


            TextView textview =
                    (TextView)activity.findViewById(R.id.status_text);

            str += Float.toString(intensity) + "磁場";
        }
        //画面にテキストビューアを追加してidをstatus_textに
        TextView textview =
                (TextView)activity.findViewById(R.id.status_text);
        // 結果をテキストとして表示
        textview.setText(str);
    }

    //位置情報が更新された時
    @Override
    public void onLocationChanged(@NonNull Location location) {
        // 得られた緯度経度の情報を表示
        double lat = location.getLatitude(); // 緯度
        double lng = location.getLongitude(); // 経度

        //画面にテキストビューアを追加してidをstatus_textに
        TextView textview =
                (TextView)activity.findViewById(R.id.sensor_text);
        String str = String.format("%.3f %.3f", lat, lng);
        textview.setText(str);

    }
}
