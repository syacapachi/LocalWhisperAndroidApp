package jp.ac.gifu_u.programmingjissen2.Transscripts;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import Utils.StringPool.StringBufferBuilderPool;
import events.Whisper.WhisperTranscriptionEvent;

/**
 * 1 回の録音 session に対応する文字起こし JSON ファイルを管理するクラスです。
 */
public class TranscriptionJsonWriter {
    /** ログ出力用タグです。 */
    private static final String TAG = TranscriptionJsonWriter.class.getSimpleName();

    /** 文字起こし JSON を保存するアプリ内部ストレージ内のディレクトリ名です。 */
    public static final String DIRECTORY_NAME = "transcriptions";

    /** JSON の形式を将来変更するときに使う schema version です。 */
    private static final int SCHEMA_VERSION = 1;

    /** 録音開始ごとに作られる session ID です。 */
    private final String sessionId;

    /** 出力先 JSON ファイルです。 */
    private final File outputFile;

    /** ファイル作成時刻です。Unix time milliseconds。 */
    private final long createdAtUnixMs;

    /** JSON に保存する文字起こし結果一覧です。 */
    private final JSONArray items = new JSONArray();

    /** 録音終了時刻です。未終了の場合は 0 です。 */
    private long finishedAtUnixMs;

    /**
     * 録音 session 用の JSON writer を作成し、空の JSON ファイルを初期保存します。
     *
     * <p>保存先は app-specific internal storage なので、追加のストレージ権限は不要です。</p>
     *
     * @param context ファイル保存先を取得するための Context
     * @param sessionId 録音 session ID
     */
    public TranscriptionJsonWriter(@NonNull Context context, String sessionId) {
        this.sessionId = sessionId;
        this.createdAtUnixMs = System.currentTimeMillis();

        final File directory = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, StringBufferBuilderPool.Join(
                    "",
                    "Failed to create transcription directory: ",
                    directory
            ));
        }

        this.outputFile = new File(
                directory,
                StringBufferBuilderPool.Join("", sanitizeFileName(sessionId), ".json")
        );
        saveQuietly();
    }

    /**
     * Whisper の推論結果を JSON に追記します。
     *
     * @param event Whisper 推論結果イベント
     */
    public synchronized void append(final WhisperTranscriptionEvent event) {
        if (event == null || event.hasError()) {
            return;
        }

        try {
            items.put(TranscriptionJsonItem.fromEvent(event).toJsonObject());
            save();
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to append transcription JSON", e);
        }
    }

    /**
     * 録音終了時刻を記録して JSON ファイルを保存します。
     */
    public synchronized void finish() {
        finishedAtUnixMs = System.currentTimeMillis();
        saveQuietly();
    }

    /**
     * JSON ファイルの保存先を返します。
     *
     * @return 保存先ファイル
     */
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * 現在の内容を JSON オブジェクトとして組み立てます。
     *
     * @return ファイルに保存するルート JSON オブジェクト
     * @throws JSONException JSON への変換に失敗した場合
     */
    @NonNull
    private JSONObject buildRootObject() throws JSONException {
        final JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("sessionId", sessionId);
        root.put("createdAtUnixMs", createdAtUnixMs);
        root.put("finishedAtUnixMs", finishedAtUnixMs);
        root.put("items", items);
        return root;
    }

    /**
     * 現在の JSON 内容をファイルに保存します。
     *
     * @throws JSONException JSON の組み立てに失敗した場合
     * @throws IOException ファイル書き込みに失敗した場合
     */
    private void save() throws JSONException, IOException {
        byte[] bytes = buildRootObject().toString(2).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream stream = new FileOutputStream(outputFile, false)) {
            stream.write(bytes);
        }
    }

    /**
     * 例外をログに出すだけで JSON 保存を試みます。
     */
    private void saveQuietly() {
        try {
            save();
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Failed to save transcription JSON", e);
        }
    }

    /**
     * session ID をファイル名として使える文字だけにします。
     *
     * @param value 元の session ID
     * @return ファイル名として安全な文字列
     */
    @NonNull
    private String sanitizeFileName(String value) {
        if (value == null || value.isEmpty()) {
            return "transcription";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
