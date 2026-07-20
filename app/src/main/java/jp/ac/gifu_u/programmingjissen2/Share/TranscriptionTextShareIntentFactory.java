package jp.ac.gifu_u.programmingjissen2.Share;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

/** 文字起こし本文を共有するIntentを作成します。 */
public final class TranscriptionTextShareIntentFactory {
    private TranscriptionTextShareIntentFactory() {
    }

    /**
     * Android共有シートを開くIntentを作成します。
     *
     * @param context FileProviderと共有シートに使うContext。例: {@code activity}
     * @param file 共有するテキストファイル。例: {@code new File(..., "file-123.txt")}
     * @param text 共有する本文。例: {@code "[00:00.000] こんにちは"}
     * @return ACTION_CHOOSER Intent。例: {@code Intent.createChooser(sendIntent, "共有先を選択")}
     * @throws IllegalArgumentException textがnull、またはfileがFileProviderの共有対象外の場合
     */
    @NonNull
    public static Intent createChooser(
            @NonNull final Context context,
            @NonNull final File file,
            final String text
    ) {
        if (text == null) {
            throw new IllegalArgumentException("Shared transcription text must not be null");
        }
        final Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
        final Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, normalizeSubject(file.getName()));
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.setClipData(ClipData.newUri(
                context.getContentResolver(),
                file.getName(),
                uri
        ));
        return Intent.createChooser(sendIntent, "共有先を選択");
    }

    /**
     * 共有件名を作成します。
     *
     * @param fileName 元ファイル名。例: {@code "file-123.txt"}
     * @return 共有件名。例: {@code "文字起こし: file-123.txt"}
     */
    @NonNull
    private static String normalizeSubject(final String fileName) {
        final String value = fileName == null || fileName.trim().isEmpty()
                ? "文字起こし"
                : fileName.trim();
        return "文字起こし: " + value;
    }
}
