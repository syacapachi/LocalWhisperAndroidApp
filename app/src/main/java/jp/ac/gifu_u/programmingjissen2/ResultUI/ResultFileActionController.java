package jp.ac.gifu_u.programmingjissen2.ResultUI;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/** 結果カードの共有・削除・編集・関連ファイルの外部表示を実行します。 */
public final class ResultFileActionController {
    private final Activity activity;
    private final TranscriptionResultRepository.Type type;
    private final Runnable onChanged;
    private final Runnable onEdited;

    /**
     * ファイル操作controllerを作成します。
     * @param activity IntentとDialogを表示するActivity。例: {@code mainActivity}
     * @param type JSON、TEXT、AUDIOのいずれか。例: {@code Type.AUDIO}
     * @param onChanged 削除後の一覧更新。例: {@code this::refresh}
     * @param onEdited 編集後のプレビュー更新。例: {@code this::reloadSelection}
     */
    public ResultFileActionController(
            @NonNull final Activity activity,
            @NonNull final TranscriptionResultRepository.Type type,
            @NonNull final Runnable onChanged,
            @NonNull final Runnable onEdited
    ) {
        this.activity = activity;
        this.type = type;
        this.onChanged = onChanged;
        this.onEdited = onEdited;
    }

    /**
     * Android共有シートで結果ファイルと本文を共有します。
     * @param entry 対象。例: {@code selectedEntry}
     */
    public void share(@NonNull final TranscriptionResultRepository.Entry entry) {
        try {
            final Uri uri = uriFor(entry.file());
            final Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(TranscriptionResultRepository.mimeType(type))
                    .putExtra(Intent.EXTRA_SUBJECT, entry.file().getName())
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newUri(
                    activity.getContentResolver(), entry.file().getName(), uri));
            if (type == TranscriptionResultRepository.Type.TEXT) {
                send.putExtra(Intent.EXTRA_TEXT,
                        TranscriptionResultRepository.read(entry.file()));
            }
            activity.startActivity(Intent.createChooser(
                    send,
                    type == TranscriptionResultRepository.Type.AUDIO
                            ? "録音を共有" : "文字起こし結果を共有"
            ));
        } catch (Exception e) {
            showError("共有できませんでした", e);
        }
    }

    /**
     * 対応アプリを選択して結果ファイルを開きます。
     * @param entry 対象。例: {@code selectedEntry}
     */
    public void openWithApp(@NonNull final TranscriptionResultRepository.Entry entry) {
        openUri(uriFor(entry.file()), TranscriptionResultRepository.mimeType(type),
                "結果を開けるアプリがありません");
    }

    /**
     * 録音WAVを端末のメディアプレイヤーで開きます。
     * @param entry 音声を持つ結果。例: {@code selectedEntry}
     */
    public void browseAudio(@NonNull final TranscriptionResultRepository.Entry entry) {
        if (entry.audioFile() == null) {
            Toast.makeText(activity, "この結果に録音音声はありません", Toast.LENGTH_SHORT).show();
            return;
        }
        openUri(uriFor(entry.audioFile()), "audio/wav", "音声を閲覧できるアプリがありません");
    }

    /**
     * 録音と同じ時刻データを持つテキストを対応アプリで開きます。
     * @param entry 録音Entry。例: {@code selectedEntry}
     */
    public void browseText(@NonNull final TranscriptionResultRepository.Entry entry) {
        if (entry.textFile() == null) {
            Toast.makeText(
                    activity,
                    "この録音に対応するテキストはありません",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        openUri(uriFor(entry.textFile()), "text/plain", "テキストを閲覧できるアプリがありません");
    }

    /**
     * 複数行編集Dialogを開き、確定時に結果ファイルを上書きします。
     * @param entry 編集対象。例: {@code selectedEntry}
     */
    public void edit(@NonNull final TranscriptionResultRepository.Entry entry) {
        final EditText editor = new EditText(activity);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setMinLines(12);
        try {
            editor.setText(TranscriptionResultRepository.read(entry.file()));
        } catch (IOException e) {
            showError("編集対象を読み込めませんでした", e);
            return;
        }
        final ScrollView scroll = new ScrollView(activity);
        scroll.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(editor);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(entry.file().getName() + " を編集")
                .setView(scroll)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> saveEdit(dialog, entry.file(), editor.getText().toString())));
        dialog.show();
    }

    /**
     * 確認後に選択した結果ファイルだけを削除します。
     * @param entry 削除対象。例: {@code selectedEntry}
     */
    public void delete(@NonNull final TranscriptionResultRepository.Entry entry) {
        final boolean audio = type == TranscriptionResultRepository.Type.AUDIO;
        new AlertDialog.Builder(activity)
                .setTitle(audio ? "録音を削除" : "結果を削除")
                .setMessage(entry.file().getName() + " を削除しますか？\n"
                        + (audio ? "関連する文字起こしは残ります。" : "関連音声は残ります。"))
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (dialog, which) -> {
                    if (TranscriptionResultRepository.delete(entry.file())) {
                        onChanged.run();
                    } else {
                        Toast.makeText(activity, "削除できませんでした", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** @param dialog 例: {@code editDialog} @param file 例: {@code a.json} @param text 例: {@code "{}"} */
    private void saveEdit(
            @NonNull final AlertDialog dialog,
            @NonNull final File file,
            @NonNull final String text
    ) {
        try {
            if (type == TranscriptionResultRepository.Type.JSON) {
                new JSONObject(text);
            }
            TranscriptionResultRepository.write(file, text);
            dialog.dismiss();
            onEdited.run();
        } catch (Exception e) {
            showError(type == TranscriptionResultRepository.Type.JSON
                    ? "正しいJSON形式で入力してください" : "保存できませんでした", e);
        }
    }

    /** @param uri 例: {@code contentUri} @param mimeType 例: {@code "audio/wav"} @param error 例: {@code "開けません"} */
    private void openUri(
            @NonNull final Uri uri,
            @NonNull final String mimeType,
            @NonNull final String error
    ) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
        }
    }

    /** @param file 例: {@code a.txt} @return FileProvider URI。例: {@code content://...} */
    @NonNull
    private Uri uriFor(@NonNull final File file) {
        return FileProvider.getUriForFile(
                activity, activity.getPackageName() + ".fileprovider", file);
    }

    /** @param message 例: {@code "保存失敗"} @param error 原因。例: {@code IOException} */
    private void showError(@NonNull final String message, @NonNull final Exception error) {
        Toast.makeText(activity, message + ": " + error.getMessage(), Toast.LENGTH_SHORT).show();
    }

    /** @param value dp。例: {@code 12} @return px。例: {@code 36} */
    private int dp(final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
