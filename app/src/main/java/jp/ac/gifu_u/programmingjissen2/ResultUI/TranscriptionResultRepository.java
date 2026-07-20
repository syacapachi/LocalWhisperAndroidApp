package jp.ac.gifu_u.programmingjissen2.ResultUI;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import jp.ac.gifu_u.programmingjissen2.Record.RecordedAudioFileWriter;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperCppModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextRepository;
import jp.ac.gifu_u.programmingjissen2.TransscriptsJSON.TranscriptionJsonWriter;

/** 結果タブで使うファイル一覧、メタデータ、本文の読み書きをまとめます。 */
public final class TranscriptionResultRepository {
    public enum Type {
        JSON(TranscriptionJsonWriter.DIRECTORY_NAME, ".json", "application/json"),
        TEXT(TranscriptionTextRepository.DIRECTORY_NAME, ".txt", "text/plain");

        final String directory;
        final String extension;
        final String mimeType;

        Type(final String directory, final String extension, final String mimeType) {
            this.directory = directory;
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    public record Entry(
            @NonNull File file,
            long recordedAtMs,
            @NonNull String model,
            long sizeBytes,
            @NonNull String tag,
            @Nullable File audioFile
    ) { }

    private TranscriptionResultRepository() { }

    /**
     * 指定種類の結果を更新日時の新しい順に取得します。
     * @param context アプリ保存先を得るContext。例: {@code activity}
     * @param type JSONまたはTEXT。例: {@code Type.TEXT}
     * @return メタデータ付き一覧。例: {@code new Entry[]{entry}}
     */
    @NonNull
    public static Entry[] list(@NonNull final Context context, @NonNull final Type type) {
        final File directory = new File(context.getFilesDir(), type.directory);
        final File[] files = directory.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(type.extension));
        if (files == null) {
            return new Entry[0];
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        final Entry[] entries = new Entry[files.length];
        for (int index = 0; index < files.length; index++) {
            entries[index] = readEntry(context, files[index]);
        }
        return entries;
    }

    /**
     * 結果本文をUTF-8で読み込みます。
     * @param file 対象ファイル。例: {@code new File("a.txt")}
     * @return 本文。例: {@code "こんにちは"}
     * @throws IOException ファイルを読み込めない場合
     */
    @NonNull
    public static String read(@NonNull final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 編集済み結果をUTF-8で上書きします。
     * @param file 対象ファイル。例: {@code new File("a.txt")}
     * @param text 新しい本文。例: {@code "修正後"}
     * @throws IOException 書き込めない場合
     */
    public static void write(@NonNull final File file, @NonNull final String text)
            throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 結果ファイルだけを削除します。関連音声や別形式の結果は残します。
     * @param file 削除対象。例: {@code new File("a.json")}
     * @return 存在しない、または削除成功ならtrue。例: {@code true}
     */
    public static boolean delete(@NonNull final File file) {
        return !file.exists() || file.delete();
    }

    /**
     * 種類に対応する共有・表示用MIME typeを返します。
     * @param type 結果種類。例: {@code Type.JSON}
     * @return MIME type。例: {@code "application/json"}
     */
    @NonNull
    public static String mimeType(@NonNull final Type type) { return type.mimeType; }

    /** @param context 例: {@code activity} @param file 例: {@code a.txt} @return 解析済みEntry */
    @NonNull
    private static Entry readEntry(@NonNull final Context context, @NonNull final File file) {
        long recordedAt = file.lastModified();
        String model = "不明";
        String tag = "未設定";
        try {
            final File json = matchingJson(context, file);
            if (json != null && json.exists()) {
                final JSONObject root = new JSONObject(read(json));
                recordedAt = root.optLong("createdAtUnixMs", recordedAt);
                final JSONArray items = root.optJSONArray("items");
                if (items != null && items.length() > 0) {
                    final JSONObject first = items.optJSONObject(0);
                    if (first != null) {
                        model = displayModel(first.optString("modelKey", model));
                        tag = first.optString("tag", tag);
                    }
                }
            }
        } catch (Exception ignored) {
            tag = "メタデータ読込失敗";
        }
        return new Entry(file, recordedAt, model, file.length(), tag,
                matchingAudio(context, file));
    }

    /** @param context 例: {@code activity} @param file 例: {@code a.txt} @return 対応JSONまたはnull */
    @Nullable
    private static File matchingJson(@NonNull final Context context, @NonNull final File file) {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            return file;
        }
        final File candidate = new File(
                new File(context.getFilesDir(), TranscriptionJsonWriter.DIRECTORY_NAME),
                baseName(file) + ".json");
        return candidate.exists() ? candidate : null;
    }

    /** @param context 例: {@code activity} @param file 例: {@code record-a.txt} @return 対応WAVまたはnull */
    @Nullable
    private static File matchingAudio(@NonNull final Context context, @NonNull final File file) {
        final File candidate = new File(
                new File(context.getFilesDir(), RecordedAudioFileWriter.DIRECTORY_NAME),
                baseName(file) + ".wav");
        return candidate.exists() ? candidate : null;
    }

    /** @param file 例: {@code a.txt} @return 拡張子なしの名前。例: {@code "a"} */
    @NonNull
    private static String baseName(@NonNull final File file) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** @param key 例: {@code "cpp-small-q8-0"} @return UI表示名。例: {@code "Whisper.cpp small・Q8_0"} */
    @NonNull
    private static String displayModel(final String key) {
        for (WhisperModelOption option : WhisperModelOption.values()) {
            if (option.key().equals(key)) {
                return option.description();
            }
        }
        for (WhisperCppModelOption option : WhisperCppModelOption.values()) {
            if (option.key().equals(key)) {
                return option.toString();
            }
        }
        return key == null || key.isEmpty() ? "不明" : key;
    }
}
