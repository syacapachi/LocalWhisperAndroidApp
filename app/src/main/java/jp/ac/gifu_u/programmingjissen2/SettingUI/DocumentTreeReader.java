package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** SAF DocumentTree直下のファイルを列挙するユーティリティです。 */
final class DocumentTreeReader {
    private DocumentTreeReader() { }

    /** DocumentProvider上の1ファイルです。 */
    record Entry(@NonNull String name, @NonNull Uri uri) { }

    /**
     * DocumentTree直下のファイルURIと名前を列挙します。
     * @param resolver ContentResolver。例: {@code context.getContentResolver()}
     * @param treeUri 選択ディレクトリ。例: {@code content://.../tree/...}
     * @return 直下ファイル一覧。例: {@code List.of(modelBin)}
     * @throws IOException ディレクトリを読み取れない場合
     */
    @NonNull
    static List<Entry> listFiles(
            @NonNull final ContentResolver resolver,
            @NonNull final Uri treeUri
    ) throws IOException {
        final String treeId = DocumentsContract.getTreeDocumentId(treeUri);
        final Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId);
        final List<Entry> result = new ArrayList<>();
        try (Cursor cursor = resolver.query(children, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        }, null, null, null)) {
            if (cursor == null) {
                throw new IOException("選択ディレクトリを読み取れません");
            }
            while (cursor.moveToNext()) {
                if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                    result.add(new Entry(
                            cursor.getString(1),
                            DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, cursor.getString(0))));
                }
            }
        } catch (RuntimeException error) {
            throw new IOException("選択ディレクトリを読み取れません", error);
        }
        return result;
    }
}
