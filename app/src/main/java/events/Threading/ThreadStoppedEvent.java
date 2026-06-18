package events.Threading;

import androidx.annotation.Nullable;

/**
 * スレッドが終了したことを通知するイベントです。
 *
 * @param threadId 待ち合わせに使う識別 ID
 * @param owner Record や Whisper など、どの処理のスレッドかを示す名前
 * @param threadName Java のスレッド名
 * @param interrupted 終了時点で interrupt されていた場合 true
 * @param errorMessage エラー終了した場合のメッセージ
 */
public record ThreadStoppedEvent(
        String threadId,
        String owner,
        String threadName,
        boolean interrupted,
        @Nullable String errorMessage
) {
    /**
     * エラー終了した停止イベントかどうかを返します。
     *
     * @return エラーメッセージがある場合 true
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
}
