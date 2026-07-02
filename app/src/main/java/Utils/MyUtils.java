package Utils;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MyUtils {
    @NonNull
    public static String prepareModelPath(@NonNull final Context context, final String assetName) throws IOException {
        File modelFile = new File(context.getFilesDir(), assetName);

        if (!modelFile.exists()) {
            try (
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
}
