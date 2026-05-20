import android.util.Log;

/**
 * 現在のスレッド状態から、実行しているクラスを取得して、タグに埋め込んでくれる
 * キータから拾ってきた。
 */
public class Logger {
    public static void log(String text) {
        StackTraceElement elem = Thread.currentThread().getStackTrace()[2];
        String tag = elem.getFileName();
        Log.d(tag, text);
    }
}
