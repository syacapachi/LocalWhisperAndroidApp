package Utils;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 現在のスレッド状態から、実行しているクラスを取得して、タグに埋め込んでくれる
 * キータから拾ってきた。
 */
public class Logger {
    public static void log(@NonNull final String text) {
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        String tag = elem.getFileName();
        Log.d(tag, text);
    }
}
