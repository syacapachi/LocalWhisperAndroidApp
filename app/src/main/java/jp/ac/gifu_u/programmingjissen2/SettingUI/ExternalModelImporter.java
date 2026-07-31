package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** SAFで選択したモデルを、native APIが読めるアプリ内部パスへコピーします。 */
public final class ExternalModelImporter {
    private static final String DIRECTORY_NAME = "imported_models";
    private ExternalModelImporter() { }

    /**
     * 選択されたWhisper.cppの.binをアプリ管理領域へコピーします。
     * @param context ContentResolverと保存先を得るContext。例: {@code activity}
     * @param source OpenDocumentで選択したURI。例: {@code content://.../ggml-small.bin}
     * @return コピー後の.bin。例: {@code files/imported_models/abc/ggml-small.bin}
     * @throws IOException ファイル名が.binでない、またはコピーできない場合
     */
    @NonNull
    public static File importWhisperBin(
            @NonNull final Context context,
            @NonNull final Uri source
    ) throws IOException {
        final String name = displayName(context.getContentResolver(), source);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".bin")) {
            throw new IOException(".binファイルを選択してください: " + name);
        }
        final File directory = modelDirectory(context, source.toString());
        final File target = new File(directory, safeName(name));
        try {
            copy(context.getContentResolver(), source, target);
        } catch (IOException error) {
            ImportedModelCleanup.rethrowAfterDelete(context, directory, error);
        }
        return target;
    }

    /**
     * 選択ディレクトリ直下のCTranslate2必須ファイルを検査してコピーします。
     * @param context ContentResolverと保存先を得るContext。例: {@code activity}
     * @param treeUri OpenDocumentTreeで選択したURI。例: {@code content://.../tree/...}
     * @return コピー後のモデルディレクトリ。例: {@code files/imported_models/abc}
     * @throws IOException model.bin、vocabulary.json、tokenizer.json、*config.jsonのいずれかがない場合
     */
    @NonNull
    public static File importCTranslate2Directory(
            @NonNull final Context context,
            @NonNull final Uri treeUri
    ) throws IOException {
        // アプリの共有データの管理オブジェクト(ここからファイルパスを取得)
        final ContentResolver resolver = context.getContentResolver();
        final List<DocumentTreeReader.Entry> entries =
                DocumentTreeReader.listFiles(resolver, treeUri);
        final List<String> names = new ArrayList<>();
        for (DocumentTreeReader.Entry entry : entries) {
            names.add(entry.name());
        }
        final List<String> missing = missingCTranslate2Files(names);
        if (!missing.isEmpty()) {
            throw new IOException("CTranslate2必須ファイルがありません: "
                    + String.join(", ", missing));
        }
        final File directory = modelDirectory(context, treeUri.toString());
        try {
            for (DocumentTreeReader.Entry entry : entries) {
                final String lower = entry.name().toLowerCase(Locale.ROOT);
                if (lower.equals("model.bin") || lower.equals("vocabulary.json")
                        || lower.equals("tokenizer.json")
                        || lower.endsWith("config.json")) {
                    copy(resolver, entry.uri(), new File(directory, safeName(entry.name())));
                }
            }
        } catch (IOException error) {
            ImportedModelCleanup.rethrowAfterDelete(context, directory, error);
        }
        return directory;
    }

    /**
     * CTranslate2ディレクトリの必須ファイル名を機械的に検査します。
     * @param names 同一ディレクトリ直下の名前。例: {@code List.of("config.json", "model.bin", "vocabulary.json")}
     * @return 不足名。すべて揃う場合は空。例: {@code List.of("*config.json")}
     */
    @NonNull
    static List<String> missingCTranslate2Files(@NonNull final Iterable<String> names) {
        boolean modelFound = false;
        boolean vocabularyFound = false;
        boolean tokenizerFound = false;
        boolean configFound = false;
        for (String name : names) {
            final String lower = name.toLowerCase(Locale.ROOT);
            modelFound |= lower.equals("model.bin");
            vocabularyFound |= lower.equals("vocabulary.json");
            tokenizerFound |= lower.equals("tokenizer.json");
            configFound |= lower.endsWith("config.json");
        }
        final List<String> missing = new ArrayList<>();
        if (!modelFound) missing.add("model.bin");
        if (!vocabularyFound) missing.add("vocabulary.json");
        if (!tokenizerFound) missing.add("tokenizer.json");
        if (!configFound) missing.add("*config.json");
        return missing;
    }

    /**
     * SAF URIの表示名を取得します。
     * @param resolver ContentResolver。共有データ(DB的なもの)の管理オブジェクト。 例: {@code context.getContentResolver()}
     * @param uri 対象URI。例: {@code content://.../model.bin}
     * @return 表示名。例: {@code "model.bin"}
     * @throws IOException 名前を取得できない場合
     */
    @NonNull
    public static String displayName(
            @NonNull final ContentResolver resolver,
            @NonNull final Uri uri
    ) throws IOException {
        // DBから、ファイル名を取得するクエリを作成。読み取りを行う。
        try (Cursor cursor = resolver.query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final String value = cursor.getString(0);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        throw new IOException("選択ファイルの名前を取得できません");
    }

    /**
     * URI内容をファイルへコピーします。
     * @param resolver ContentResolver。例: {@code context.getContentResolver()}
     * @param source 入力URI。例: {@code content://.../model.bin}
     * @param target 出力。例: {@code files/imported_models/a/model.bin}
     * @throws IOException 入出力を開けない場合
     */
    private static void copy(
            @NonNull final ContentResolver resolver,
            @NonNull final Uri source,
            @NonNull final File target
    ) throws IOException {
        try (InputStream input = resolver.openInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IOException("選択ファイルを開けません");
            final byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
    }

    /**
     * インポート先ディレクトリを作成します。
     * @param context filesDirを得るContext。例: {@code activity}
     * @param sourceKey URI文字列。例: {@code "content://model"}
     * @return 作成済みディレクトリ。例: {@code files/imported_models/a1}
     * @throws IOException 作成できない場合
     */
    @NonNull
    private static File modelDirectory(
            @NonNull final Context context,
            @NonNull final String sourceKey
    ) throws IOException {
        final File directory = new File(
                new File(context.getFilesDir(), DIRECTORY_NAME),
                Integer.toUnsignedString(sourceKey.hashCode(), 16)
                        + "-" + Long.toHexString(System.nanoTime()));
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("モデル保存先を作成できません: " + directory);
        }
        return directory;
    }

    /**
     * ファイル名からパス区切り文字を除去します。
     * @param value 元の名前。例: {@code "model.bin"}
     * @return 安全な名前。例: {@code "model.bin"}
     */
    @NonNull
    private static String safeName(@NonNull final String value) {
        return value.replace('/', '_').replace('\\', '_');
    }

}
