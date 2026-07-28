package jp.ac.gifu_u.programmingjissen2.SettingUI;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ExternalTranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;

/** 外部モデル定義のJSON変換とファイル入出力だけを担当します。 */
final class ExternalModelJsonStore {
    private static final int SCHEMA_VERSION = 1;
    private ExternalModelJsonStore() { }

    /**
     * 外部モデルJSONを読みます。
     * @param file 入力。例: {@code external_models.json}
     * @return 有効なモデル一覧。ファイル未作成時は空。例: {@code List.of(model)}
     * @throws IOException 読み込めない場合
     * @throws JSONException JSON構造が不正な場合
     */
    @NonNull
    static List<ExternalTranscriptionModel> read(@NonNull final File file)
            throws IOException, JSONException {
        final List<ExternalTranscriptionModel> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        final JSONArray models = new JSONObject(readText(file)).optJSONArray("models");
        if (models == null) {
            return result;
        }
        for (int index = 0; index < models.length(); index++) {
            try {
                final JSONObject value = models.getJSONObject(index);
                result.add(new ExternalTranscriptionModel(
                        value.getString("key"),
                        value.getString("name"),
                        value.getString("modelPath"),
                        WhisperInferenceEngine.fromJsonValue(value.has("executionModel")
                                ? value.getString("executionModel")
                                : value.getString("engine")),
                        value.optString("computeType", "int8")
                ));
            } catch (JSONException | IllegalArgumentException ignored) {
                // ほかの有効なモデルを利用できるよう、不正な1要素だけを除外します。
            }
        }
        return result;
    }

    /**
     * 外部モデル一覧をJSONへ書き込みます。
     * @param file 出力。例: {@code external_models.json}
     * @param models 保存対象。例: {@code List.of(model)}
     * @throws IOException JSON生成または書き込みに失敗した場合
     */
    static void write(
            @NonNull final File file,
            @NonNull final List<ExternalTranscriptionModel> models
    ) throws IOException {
        try {
            final JSONArray values = new JSONArray();
            for (ExternalTranscriptionModel model : models) {
                values.put(new JSONObject()
                        .put("key", model.key())
                        .put("name", model.label())
                        .put("modelPath", model.modelPath())
                        .put("executionModel", model.engine().jsonValue())
                        .put("computeType", model.computeType()));
            }
            final byte[] bytes = new JSONObject()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("models", values)
                    .toString(2)
                    .getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }
        } catch (JSONException error) {
            throw new IOException("External model JSON could not be built", error);
        }
    }

    /**
     * UTF-8ファイルを読みます。
     * @param file 入力。例: {@code external_models.json}
     * @return 本文。例: {@code "{\"models\":[]}"}
     * @throws IOException 読み込めない場合
     */
    @NonNull
    private static String readText(@NonNull final File file) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            final byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
