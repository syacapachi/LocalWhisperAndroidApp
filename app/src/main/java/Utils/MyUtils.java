package Utils;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MyUtils {
    /**
     * assetsの単一モデルファイルを内部ストレージへコピーします。
     *
     * @param context assetsとfilesDirを提供するContext。例: {@code service}
     * @param assetName asset名。例: {@code "ggml-base.bin"}
     * @return コピー先の絶対パス。例: {@code "/data/user/0/.../ggml-base.bin"}
     * @throws IOException assetが存在しない、またはコピーできない場合
     */
    @NonNull
    public static String prepareModelPath(@NonNull final Context context, final String assetName) throws IOException {
        // アプリの絶対パス /data/.../ggml-base.bin
        File modelFile = new File(context.getFilesDir(), assetName);

        if (!modelFile.exists()) {
            try (
                    // .../assets/ggml-base.bin
                    InputStream in = context.getAssets().open(assetName);
                    OutputStream out = new FileOutputStream(modelFile)
            ) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }

        return modelFile.getAbsolutePath();
    }

    /**
     * assetsのディレクトリを内部ストレージへ再帰コピーします。
     * CTranslate2モデルのconfig.json、model.bin、vocabulary.json,tokenizer.jsonをまとめて準備する用途です。
     *
     * @param context assetsとfilesDirを提供するContext。例: {@code service}
     * @param assetDirectory assetディレクトリ。例: {@code "ctranslate2/base"}
     * @return コピー先ディレクトリの絶対パス。例: {@code "/data/user/0/.../ctranslate2/base"}
     * @throws IOException ディレクトリが空、必要なmodel.binがない、またはコピーできない場合
     */
    @NonNull
    public static String prepareModelDirectory(
            @NonNull final Context context,
            @NonNull final String assetDirectory
    ) throws IOException {
        final File destination = new File(context.getFilesDir(), assetDirectory);
        final File modelFile = new File(destination, "model.bin");
        if (!modelFile.isFile()) {
            copyAssetDirectory(context.getAssets(), assetDirectory, destination);
        }
        if (!modelFile.isFile()) {
            throw new IOException("CTranslate2 model.bin not found in assets/" + assetDirectory);
        }
        return destination.getAbsolutePath();
    }

    /**
     * AssetManager内のツリーをファイルシステムへコピーします。
     *
     * @param assets 読み込み元。例: {@code context.getAssets()}
     * @param assetPath コピー元相対パス。例: {@code "ctranslate2/base"}
     * @param destination コピー先。例: {@code new File(filesDir, assetPath)}
     * @throws IOException assetを列挙・作成・コピーできない場合
     */
    private static void copyAssetDirectory(
            @NonNull final AssetManager assets,
            @NonNull final String assetPath,
            @NonNull final File destination
    ) throws IOException {
        final String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assets, assetPath, destination);
            return;
        }
        if (!destination.isDirectory() && !destination.mkdirs()) {
            throw new IOException("Failed to create directory: " + destination);
        }
        for (String child : children) {
            copyAssetDirectory(
                    assets,
                    assetPath + "/" + child,
                    new File(destination, child)
            );
        }
    }

    /**
     * assetファイルを1件コピーします。
     *
     * @param assets 読み込み元。例: {@code context.getAssets()}
     * @param assetPath ファイルパス。例: {@code "ctranslate2/base/model.bin"}
     * @param destination コピー先ファイル。例: {@code new File(modelDir, "model.bin")}
     * @throws IOException 入出力に失敗した場合
     */
    private static void copyAssetFile(
            @NonNull final AssetManager assets,
            @NonNull final String assetPath,
            @NonNull final File destination
    ) throws IOException {
        final File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }
        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(destination)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
