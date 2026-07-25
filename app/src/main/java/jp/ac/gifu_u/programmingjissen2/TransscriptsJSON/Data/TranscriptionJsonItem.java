package jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.Data;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;
import org.json.JSONException;
import org.json.JSONObject;

import events.Whisper.WhisperTranscriptionEvent;
/**
 * JSON に保存する 1 件分の文字起こし結果です。
 *
 * <p>フィールドをこのクラスに集めておくことで、あとから信頼度、話者 ID、
 * 音声ファイル名などを増やしやすくしています。</p>
 */
public class TranscriptionJsonItem {
    /** 同じ録音 session 内での結果番号です。 */
    public final int sequence;

    /** 録音開始地点から、この結果が対象にしている音声の開始位置までの時間です。ミリ秒。 */
    public final long recordingTimeMs;

    /** この結果が対象にしている音声の長さです。ミリ秒。 */
    public final long durationMs;

    /** Whisper 推論 1 回にかかった処理時間です。ミリ秒。 */
    public final long processingTimeMs;

    /** Whisper が出力した文字起こし本文です。 */
    public final String text;

    /** この結果の直後に話者が変わった可能性がある場合 true です。 */
    public final boolean speakerChanged;

    /**
     * JSON 保存用の文字起こし結果を作成します。
     *
     * @param sequence 同一session内の番号。例: {@code 0}
     * @param recordingTimeMs 録音開始からの開始時刻ms。例: {@code 1000}
     * @param durationMs 対象音声の長さms。例: {@code 5000}
     * @param processingTimeMs 推論時間ms。例: {@code 1200}
     * @param text 文字起こし本文。例: {@code "こんにちは"}
     * @param speakerChanged 話者変化の可能性。例: {@code false}
     */
    public TranscriptionJsonItem(
            int sequence,
            long recordingTimeMs,
            long durationMs,
            long processingTimeMs,
            String text,
            boolean speakerChanged
    ) {
        this.sequence = sequence;
        this.recordingTimeMs = recordingTimeMs;
        this.durationMs = durationMs;
        this.processingTimeMs = Math.max(0, processingTimeMs);
        this.text = text == null ? "" : text;
        this.speakerChanged = speakerChanged;
    }

    /**
     * Whisper 推論イベントから JSON 保存用データを作成します。
     *
     * @param event Whisper 推論結果イベント。例: {@code transcriptionEvent}
     * @return JSON 保存用の 1 件分データ。例: {@code item}
     * @throws NullPointerException eventがnullの場合
     */
    @NonNull
    @Contract("_ -> new")
    public static TranscriptionJsonItem fromEvent(@NonNull final WhisperTranscriptionEvent event) {
        return new TranscriptionJsonItem(
                event.sequence(),
                event.startMs(),
                event.durationMs(),
                event.processingTimeMs(),
                event.text(),
                event.speakerChanged()
        );
    }

    /**
     * この文字起こし結果を {@link JSONObject} に変換します。
     *
     * @return JSON 出力用オブジェクト。例: {@code {"sequence":0,"text":"こんにちは"}}
     * @throws JSONException JSON への変換に失敗した場合
     */
    public JSONObject toJsonObject() throws JSONException {
        final JSONObject object = new JSONObject();
        object.put("sequence", sequence);
        object.put("recordingTimeMs", recordingTimeMs);
        object.put("durationMs", durationMs);
        object.put("processingTimeMs", processingTimeMs);
        object.put("text", text);
        object.put("speakerChanged", speakerChanged);
        return object;
    }
}
