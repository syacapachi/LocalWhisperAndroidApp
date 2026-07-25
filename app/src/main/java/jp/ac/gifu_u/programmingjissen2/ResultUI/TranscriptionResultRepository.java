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
        TEXT(TranscriptionTextRepository.DIRECTORY_NAME, ".txt", "text/plain"),
        AUDIO(RecordedAudioFileWriter.DIRECTORY_NAME, ".wav", "audio/wav");

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
            @Nullable File audioFile,
            @Nullable File textFile
    ) { }

    private TranscriptionResultRepository() { }

    /**
     * 指定種類の結果を更新日時の新しい順に取得します。
     * @param context アプリ保存先を得るContext。例: {@code activity}
     * @param type JSON、TEXT、AUDIOのいずれか。例: {@code Type.AUDIO}
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
            entries[index] = readEntry(context, files[index], type);
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

    /**
     * ファイル本体と同じ時刻データを持つJSONからメタデータを読みます。
     * @param context 保存先を得るContext。例: {@code activity}
     * @param file 一覧対象。例: {@code new File("record-1a-2b.wav")}
     * @param type 対象種類。例: {@code Type.AUDIO}
     * @return 解析済みEntry。例: {@code entry}
     */
    @NonNull
    private static Entry readEntry(
            @NonNull final Context context,
            @NonNull final File file,
            @NonNull final Type type
    ) {
        long recordedAt = file.lastModified();
        String model = "不明";
        String tag = "未設定";
        try {
            final File json = matchingJson(context, file);
            if (json != null && json.exists()) {
                final JSONObject root = new JSONObject(read(json));
                recordedAt = root.optLong("createdAtUnixMs", recordedAt);
                model = displayModel(root.optString("modelKey", model));
                tag = root.optString("tag", tag);
                // Version 1 の互換
                final JSONArray items = root.optJSONArray("items");
                if ((model.equals("不明") || tag.equals("未設定"))
                        && items != null && items.length() > 0) {
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
        final File audio = type == Type.AUDIO ? file : matchingAudio(context, file);
        final File text = type == Type.TEXT ? file : matchingText(context, file);
        return new Entry(file, recordedAt, model, file.length(), tag, audio, text);
    }

    /**
     * 同名、または末尾の時刻データが一致するJSONを探します。
     * @param context 保存先を得るContext。例: {@code activity}
     * @param file 基準ファイル。例: {@code new File("record-1a-2b.wav")}
     * @return 対応JSON。見つからない場合はnull。例: {@code live-0-1a-2b.json}
     */
    @Nullable
    private static File matchingJson(@NonNull final Context context, @NonNull final File file) {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
            return file;
        }
        final File candidate = new File(
                new File(context.getFilesDir(), TranscriptionJsonWriter.DIRECTORY_NAME),
                baseName(file) + ".json");
        return candidate.exists() ? candidate : findFirstTimeMatch(
                context, file, TranscriptionJsonWriter.DIRECTORY_NAME, ".json");
    }

    /**
     * 末尾の時刻データが一致する最初のWAVを探します。
     * @param context 保存先を得るContext。例: {@code activity}
     * @param file 基準ファイル。例: {@code new File("live-0-1a-2b.txt")}
     * @return 対応WAV。見つからない場合はnull。例: {@code record-1a-2b.wav}
     */
    @Nullable
    private static File matchingAudio(@NonNull final Context context, @NonNull final File file) {
        final File candidate = new File(
                new File(context.getFilesDir(), RecordedAudioFileWriter.DIRECTORY_NAME),
                baseName(file) + ".wav");
        return candidate.exists() ? candidate : findFirstTimeMatch(
                context, file, RecordedAudioFileWriter.DIRECTORY_NAME, ".wav");
    }

    /**
     * 末尾の時刻データが一致する最初のテキストを探します。
     * @param context 保存先を得るContext。例: {@code activity}
     * @param file 基準ファイル。例: {@code new File("record-1a-2b.wav")}
     * @return 対応テキスト。見つからない場合はnull。例: {@code live-0-1a-2b.txt}
     */
    @Nullable
    private static File matchingText(@NonNull final Context context, @NonNull final File file) {
        return findFirstTimeMatch(
                context, file, TranscriptionTextRepository.DIRECTORY_NAME, ".txt");
    }

    /**
     * 指定ディレクトリから時刻データ一致ファイルを名前順で探します。
     * @param context 保存先を得るContext。例: {@code activity}
     * @param source 基準ファイル。例: {@code new File("record-1a-2b.wav")}
     * @param directoryName 検索ディレクトリ。例: {@code "transcription_texts"}
     * @param extension 拡張子。例: {@code ".txt"}
     * @return 名前順で最初の一致ファイル。見つからない場合はnull
     */
    @Nullable
    private static File findFirstTimeMatch(
            @NonNull final Context context,
            @NonNull final File source,
            @NonNull final String directoryName,
            @NonNull final String extension
    ) {
        final String timeData = extractTimeData(source.getName());
        if (timeData == null) {
            return null;
        }
        final File directory = new File(context.getFilesDir(), directoryName);
        final File[] matches = directory.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(extension)
                        && timeData.equals(extractTimeData(name)));
        if (matches == null || matches.length == 0) {
            return null;
        }
        Arrays.sort(matches, Comparator.comparing(File::getName));
        return matches[0];
    }

    /**
     * ファイル名末尾の「16進時刻-16進時刻」を関連付けキーとして抽出します。
     * @param fileName ファイル名。例: {@code "live-0-19f99e5b391-317248c70b73.txt"}
     * @return 時刻データ。例: {@code "19f99e5b391-317248c70b73"}。形式不正時はnull
     */
    @Nullable
    static String extractTimeData(@NonNull final String fileName) {
        final String name = baseName(new File(fileName));
        final int lastSeparator = name.lastIndexOf('-');
        if (lastSeparator <= 0 || lastSeparator == name.length() - 1) {
            return null;
        }
        final int previousSeparator = name.lastIndexOf('-', lastSeparator - 1);
        if (previousSeparator < 0 || previousSeparator == lastSeparator - 1) {
            return null;
        }
        final String first = name.substring(previousSeparator + 1, lastSeparator);
        final String second = name.substring(lastSeparator + 1);
        if (!first.matches("[0-9a-fA-F]+") || !second.matches("[0-9a-fA-F]+")) {
            return null;
        }
        return first + "-" + second;
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
