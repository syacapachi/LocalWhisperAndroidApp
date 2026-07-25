package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

/** 検証失敗したモデルをアプリのインポート管理領域だけから削除します。 */
final class ImportedModelCleanup {
    private static final String DIRECTORY_NAME = "imported_models";
    private ImportedModelCleanup() { }

    /**
     * 検証失敗したインポートモデルを管理領域から再帰削除します。
     * @param context filesDirを得るContext。例: {@code activity}
     * @param imported importWhisperBin等の戻り値。例: {@code importedModel}
     * @return 全対象を削除できた場合true。例: {@code true}
     * @throws IOException 対象がimported_models外、または正規パスを解決できない場合
     */
    static boolean delete(
            @NonNull final Context context,
            @NonNull final File imported
    ) throws IOException {
        final File root = new File(context.getFilesDir(), DIRECTORY_NAME).getCanonicalFile();
        final File value = imported.getCanonicalFile();
        if (!value.toPath().startsWith(root.toPath()) || value.equals(root)) {
            throw new IOException("インポート管理領域外は削除できません: " + value);
        }
        final File target = value.isDirectory() ? value : value.getParentFile();
        return target != null && deleteRecursively(target);
    }

    /**
     * コピー途中の管理ディレクトリを削除して元のIOExceptionを再送出します。
     * @param context filesDirを得るContext。例: {@code activity}
     * @param directory コピー途中のディレクトリ。例: {@code imported_models/a1}
     * @param cause コピー失敗原因。例: {@code new IOException("read failed")}
     * @throws IOException 常にcauseを送出。削除失敗はsuppressedへ追加
     */
    static void rethrowAfterDelete(
            @NonNull final Context context,
            @NonNull final File directory,
            @NonNull final IOException cause
    ) throws IOException {
        try {
            if (!delete(context, directory)) {
                cause.addSuppressed(new IOException("コピー途中のモデルを削除できませんでした"));
            }
        } catch (IOException cleanupError) {
            cause.addSuppressed(cleanupError);
        }
        throw cause;
    }

    /**
     * 管理領域内と確認済みのファイルまたはディレクトリを削除します。
     * @param target 削除対象。例: {@code files/imported_models/a1}
     * @return すべて削除成功または既に存在しない場合true。例: {@code true}
     */
    private static boolean deleteRecursively(@NonNull final File target) {
        boolean success = true;
        final File[] children = target.listFiles();
        if (children != null) {
            for (File child : children) {
                success &= deleteRecursively(child);
            }
        }
        return (!target.exists() || target.delete()) && success;
    }
}
