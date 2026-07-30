package jp.ac.gifu_u.programmingjissen2.TranscriptionText;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.Data.TranscriptionJsonItem;

/** TranscriptionJsonItem からテキスト出力に必要な時間と本文を取り出す class です。 */
public final class TranscriptionJsonItemTextExtractor {
    private TranscriptionJsonItemTextExtractor() {
    }

    /**
     * JSON保存用 item から時間と本文を取り出します。
     *
     * @param item JSON保存用文字起こし item。例: {@code TranscriptionJsonItem.fromEvent(event)}
     * @return テキスト化用 item。例: {@code new TranscriptionTextItem(1000, "こんにちは")}
     */
    @NonNull
    public static TranscriptionTextItem extract(@NonNull final TranscriptionJsonItem item) {
        return new TranscriptionTextItem(item.recordingTimeMs, item.text);
    }

    /**
     * JSONObject の item から時間と本文を取り出します。
     *
     * @param object JSON の items 配列内オブジェクト。例: {@code root.getJSONArray("items").getJSONObject(0)}
     * @return テキスト化用 item。例: {@code new TranscriptionTextItem(1000, "こんにちは")}
     * @throws JSONException recordingTimeMs などの読み取りに失敗した場合
     */
    @NonNull
    public static TranscriptionTextItem extract(@NonNull final JSONObject object)
            throws JSONException {
        return new TranscriptionTextItem(
                object.optLong("recordingTimeMs", 0),
                object.optString("text", "")
        );
    }

    /**
     * 文字起こしJSONルートから items の時間と本文をまとめて取り出します。
     *
     * @param root 文字起こしJSONのルート。例: {@code new JSONObject(jsonText)}
     * @return items から取り出したテキスト化用 item 一覧。例: {@code List.of(new TranscriptionTextItem(0, "..."))}
     * @throws JSONException items がJSONとして読めない場合
     */
    @NonNull
    public static List<TranscriptionTextItem> extractItems(@NonNull final JSONObject root)
            throws JSONException {
        final JSONArray items = root.optJSONArray("items");
        final List<TranscriptionTextItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }

        for (int i = 0; i < items.length(); i++) {
            result.add(extract(items.getJSONObject(i)));
        }
        return result;
    }
}
