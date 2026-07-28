package jp.ac.gifu_u.programmingjissen2.TranscriptionText;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
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

/** 文字起こしテキストファイルの保存と読み込みを担当する class です。 */
public final class TranscriptionTextRepository {
    public static final String DIRECTORY_NAME = "transcription_texts";

    private TranscriptionTextRepository() {
    }

    /**
     * 文字起こしJSONから整形テキストを作成し、保存します。
     *
     * @param context 保存先を取得する Context。例: {@code activity}
     * @param jsonFile TranscriptionJsonWriter が保存した JSON ファイル。例: {@code new File(getFilesDir(), "transcriptions/a.json")}
     * @return 保存したテキストファイル。例: {@code /data/.../transcription_texts/a.txt}
     * @throws IOException JSON読み込みまたはテキスト保存に失敗した場合
     * @throws JSONException JSON構造が読めない場合
     */
    @NonNull
    public static File saveFromJsonFile(
            @NonNull final Context context,
            @NonNull final File jsonFile
    ) throws IOException, JSONException {
        final String jsonText = readUtf8(jsonFile);
        final JSONObject root = new JSONObject(jsonText);
        final String formatted = TranscriptionTextFormatter.format(
                TranscriptionJsonItemTextExtractor.extractItems(root)
        );
        return saveText(
                context,
                removeExtension(jsonFile.getName()),
                TranscriptionTextFilter.filter(formatted)
        );
    }

    /**
     * 整形済み文字起こしテキストをフィルターして保存します。
     *
     * @param context 保存先を取得する Context。例: {@code activity}
     * @param baseName 拡張子なしの保存名。例: {@code "file-1a2b"}
     * @param text 保存するテキスト。例: {@code "[00:00.000] こんにちは\n"}
     * @return 保存したテキストファイル。例: {@code /data/.../transcription_texts/file-1a2b.txt}
     * @throws IOException テキスト保存に失敗した場合
     */
    @NonNull
    public static File saveFilteredText(
            @NonNull final Context context,
            @NonNull final String baseName,
            final String text
    ) throws IOException {
        return saveText(context, baseName, TranscriptionTextFilter.filter(text));
    }

    /**
     * 保存済みテキストファイルを新しい順に取得します。
     *
     * @param context 保存先を取得する Context。例: {@code activity}
     * @return 保存済み .txt ファイル一覧。例: {@code new File[]{file}}
     */
    @NonNull
    public static File[] listTextFiles(@NonNull final Context context) {
        final File directory = getDirectory(context);
        final File[] files = directory.listFiles(
                (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt")
        );
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    /**
     * UTF-8 テキストファイルを読み込みます。
     *
     * @param file 読み込むテキストファイル。例: {@code new File(..., "a.txt")}
     * @return ファイル本文。例: {@code "[00:00.000] こんにちは\n"}
     * @throws IOException ファイル読み込みに失敗した場合
     */
    @NonNull
    public static String readText(@NonNull final File file) throws IOException {
        return readUtf8(file);
    }

    /**
     * UTF-8ファイルを読み込みます。
     *
     * @param file 読み込むファイル。例: {@code new File(..., "a.txt")}
     * @return ファイル本文。例: {@code "こんにちは"}
     * @throws IOException ファイル読み込みに失敗した場合
     */
    @NonNull
    private static String readUtf8(@NonNull final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final byte[] buffer = new byte[4096];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * テキストを指定名で保存します。
     *
     * @param context 保存先を取得する Context。例: {@code activity}
     * @param baseName 拡張子なしの保存名。例: {@code "session-1"}
     * @param text 保存する本文。例: {@code "[00:00.000] こんにちは\n"}
     * @return 保存したファイル。例: {@code /data/.../transcription_texts/session-1.txt}
     * @throws IOException ディレクトリ作成またはファイル書き込みに失敗した場合
     */
    @NonNull
    private static File saveText(
            @NonNull final Context context,
            @NonNull final String baseName,
            @NonNull final String text
    ) throws IOException {
        final File directory = getDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory);
        }

        final File file = new File(directory, sanitizeFileName(baseName) + ".txt");
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /**
     * テキスト保存ディレクトリを返します。
     *
     * @param context 保存先を取得する Context。例: {@code activity}
     * @return 保存ディレクトリ。例: {@code /data/.../files/transcription_texts}
     */
    @NonNull
    private static File getDirectory(@NonNull final Context context) {
        return new File(context.getFilesDir(), DIRECTORY_NAME);
    }

    /**
     * ファイル名に使えない文字を置換します。
     *
     * @param value 元の名前。例: {@code "file:1"}
     * @return 安全な名前。例: {@code "file_1"}
     */
    @NonNull
    private static String sanitizeFileName(final String value) {
        if (value == null || value.isEmpty()) {
            return "transcription";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * ファイル名から最後の拡張子を取り除きます。
     *
     * @param fileName ファイル名。例: {@code "a.json"}
     * @return 拡張子を除いた名前。例: {@code "a"}
     */
    @NonNull
    private static String removeExtension(@NonNull final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
