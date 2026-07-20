package jp.ac.gifu_u.programmingjissen2.FileImport;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

/** 外部アプリの「アプリで開く」Intentから対応メディアURIを読み取ります。 */
public final class ExternalMediaIntentReader {
    private ExternalMediaIntentReader() {
    }

    /**
     * ACTION_VIEWで渡された音声または動画URIを返します。
     *
     * @param intent 外部Intent。例: {@code new Intent(Intent.ACTION_VIEW, uri).setType("audio/mpeg")}
     * @return 対応URI。例: {@code content://media/external/audio/media/1}。非対応時はnull
     */
    @Nullable
    public static Uri readSupportedUri(@Nullable final Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }
        final Uri uri = intent.getData();
        if (uri == null || !isSupportedMimeType(intent.getType())) {
            return null;
        }
        return uri;
    }

    /**
     * MIMEタイプが音声または動画か判定します。
     *
     * @param mimeType 判定対象。例: {@code "video/mp4"}
     * @return 対応する場合true。例: {@code true}
     */
    private static boolean isSupportedMimeType(@Nullable final String mimeType) {
        return mimeType != null
                && (mimeType.startsWith("audio/") || mimeType.startsWith("video/"));
    }
}
