package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import Utils.MyUtils;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;

/** 同梱・外部モデルの定義からnative APIへ渡す実パスを解決します。 */
public final class ModelPathResolver {
    private ModelPathResolver() { }

    /**
     * モデルの実ファイルまたはディレクトリを解決します。
     * @param context assets展開先を得るContext。例: {@code activity}
     * @param model モデル定義。例: {@code WhisperModelOption.CT2_SMALL_INT8}
     * @return native APIへ渡す絶対パス。例: {@code "/data/user/0/.../model.bin"}
     * @throws IOException assets展開失敗、または外部パスが存在しない場合
     */
    @NonNull
    public static String resolve(
            @NonNull final Context context,
            @NonNull final ITranscriptionModel model
    ) throws IOException {
        if (model.bundled()) {
            return model.engine() == WhisperInferenceEngine.CTRANSLATE2
                    ? MyUtils.prepareModelDirectory(context, model.modelPath())
                    : MyUtils.prepareModelPath(context, model.modelPath());
        }
        final File file = new File(model.modelPath());
        if (!file.exists()) {
            throw new IOException("External model path not found: " + model.modelPath());
        }
        return file.getCanonicalPath();
    }
}
