package events.Whisper;

import android.util.Log;

import java.util.Objects;
import java.util.function.Consumer;

import events.AwaitEvent.EventAwaiter;

/**
 * 指定した録音 session の Whisper 推論結果を 1 件だけ待つ Awaiter です。
 */
public class WhisperTranscriptionAwaiter extends EventAwaiter<WhisperTranscriptionEvent> {
    /** ログ出力用タグです。 */
    private static final String TAG = WhisperTranscriptionAwaiter.class.getSimpleName();

    /** 待ち受け対象の録音 session ID です。 */
    private String sessionId;

    /** 対象イベントを受け取ったときに呼ぶ callback です。 */
    private Consumer<WhisperTranscriptionEvent> callback;

    /**
     * 待ち受ける録音 session と callback を設定します。
     *
     * @param sessionId 待ち受け対象の録音 session ID
     * @param callback イベント受信時の処理
     */
    public void initialize(
            String sessionId,
            Consumer<WhisperTranscriptionEvent> callback
    ) {
        this.sessionId = sessionId;
        this.callback = callback;
    }

    /**
     * 受信イベントが待ち受け対象の session か判定します。
     */
    @Override
    protected boolean match(WhisperTranscriptionEvent event) {
        return Objects.equals(sessionId, event.sessionId());
    }

    /**
     * 対象 session の推論結果を callback へ渡します。
     */
    @Override
    protected void onReceive(WhisperTranscriptionEvent event) {
        if (callback == null) {
            return;
        }

        try {
            callback.accept(event);
        } catch (Exception e) {
            Log.e(TAG, "Whisper transcription callback error", e);
        }
    }

    /**
     * この Awaiter が購読するイベント型を返します。
     */
    @Override
    protected Class<WhisperTranscriptionEvent> getEventType() {
        return WhisperTranscriptionEvent.class;
    }

    /**
     * ObjectPool 返却時に保持している session と callback を消します。
     */
    @Override
    public void onReset() {
        sessionId = null;
        callback = null;
    }
}
