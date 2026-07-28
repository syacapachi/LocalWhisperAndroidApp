package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ExternalTranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperCppModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.Transcription.ModelLoadProbe;

/** 外部モデルJSONを保存し、同梱モデルと結合して提供します。 */
public final class ExternalModelRepository {
    private static final String TAG = ExternalModelRepository.class.getSimpleName();
    private static final String DIRECTORY_NAME = "model_definitions";
    private static final String FILE_NAME = "external_models.json";
    private final File jsonFile;

    /**
     * アプリ内部の外部モデルJSONを管理します。
     * @param context 保存先を得るContext。例: {@code activity}
     * @throws NullPointerException contextがnullの場合
     */
    public ExternalModelRepository(@NonNull final Context context) {
        final File directory = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "Could not create model definition directory: " + directory);
        }
        jsonFile = new File(directory, FILE_NAME);
    }

    /**
     * 同梱モデルとJSONの外部モデルを実行方式で絞って返します。
     * @param engine 実行方式。例: {@code WhisperInferenceEngine.CTRANSLATE2}
     * @return 選択肢。例: {@code new TranscriptionModel[]{...}}
     */
    @NonNull
    public ITranscriptionModel[] list(@NonNull final WhisperInferenceEngine engine) {
        final List<ITranscriptionModel> result = new ArrayList<>();
        if (engine == WhisperInferenceEngine.CTRANSLATE2) {
            java.util.Collections.addAll(result, WhisperModelOption.values());
        } else {
            java.util.Collections.addAll(result, WhisperCppModelOption.values());
        }
        for (ExternalTranscriptionModel model : loadExternal()) {
            if (model.engine() == engine) {
                result.add(model);
            }
        }
        return result.toArray(new ITranscriptionModel[0]);
    }

    /**
     * 保存キーから同梱または外部モデルを検索します。
     * @param key 保存キー。例: {@code "external-a12b"}
     * @param engine 必須の実行方式。例: {@code WhisperInferenceEngine.WHISPER_CPP}
     * @return 一致モデル。見つからない場合はnull
     */
    @Nullable
    public ITranscriptionModel find(
            @Nullable final String key,
            @NonNull final WhisperInferenceEngine engine
    ) {
        if (key == null) {
            return null;
        }
        for (ITranscriptionModel model : list(engine)) {
            if (model.key().equals(key)) {
                return model;
            }
        }
        return null;
    }

    /**
     * 読み込み検証済みモデルをJSONへ追加または同じパスの定義へ上書きします。
     * @param name UI名。例: {@code "会議用small"}
     * @param modelPath 検証済み絶対パス。例: {@code "/sdcard/models/small"}
     * @param computeType CTranslate2計算型。例: {@code "int8"}
     * @param probe 両エンジンの検証結果。例: {@code ModelLoadProbe.probe(path, "int8")}
     * @return 保存したモデル。例: {@code externalModel}
     * @throws IOException JSONを書き込めない場合
     * @throws IllegalArgumentException 検証失敗、または名前が空の場合
     */
    @NonNull
    public ExternalTranscriptionModel saveVerified(
            @NonNull final String name,
            @NonNull final String modelPath,
            @NonNull final String computeType,
            @NonNull final ModelLoadProbe.Result probe
    ) throws IOException {
        if (!probe.loaded() || probe.detectedEngine() == null) {
            throw new IllegalArgumentException("The model could not be loaded");
        }
        final List<ExternalTranscriptionModel> models = loadExternal();
        String key = "external-" + Integer.toUnsignedString(modelPath.hashCode(), 16);
        for (int index = models.size() - 1; index >= 0; index--) {
            if (models.get(index).modelPath().equals(modelPath)) {
                key = models.get(index).key();
                models.remove(index);
            }
        }
        final String savedComputeType =
                probe.detectedEngine() == WhisperInferenceEngine.CTRANSLATE2
                        && computeType.trim().isEmpty() ? "int8" : computeType.trim();
        final ExternalTranscriptionModel saved = new ExternalTranscriptionModel(
                key, name, modelPath, probe.detectedEngine(), savedComputeType);
        models.add(saved);
        ExternalModelJsonStore.write(jsonFile, models);
        return saved;
    }

    /**
     * 外部モデルJSONを読みます。不正要素は無視し、ファイル全体の失敗はログへ記録します。
     * @return 有効な外部モデル一覧。例: {@code List.of(model)}
     */
    @NonNull
    public List<ExternalTranscriptionModel> loadExternal() {
        try {
            return ExternalModelJsonStore.read(jsonFile);
        } catch (Exception error) {
            Log.e(TAG, "External model JSON could not be read", error);
            return new ArrayList<>();
        }
    }

    /**
     * JSON保存先を返します。
     * @return 保存ファイル。例: {@code files/model_definitions/external_models.json}
     */
    @NonNull
    public File jsonFile() { return jsonFile; }

}
