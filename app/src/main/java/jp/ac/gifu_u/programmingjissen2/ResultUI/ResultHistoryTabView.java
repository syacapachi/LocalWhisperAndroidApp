package jp.ac.gifu_u.programmingjissen2.ResultUI;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 固定された一覧領域と独立スクロール可能な本文領域を持つ結果タブです。 */
public final class ResultHistoryTabView {
    private final Activity activity;
    private final TranscriptionResultRepository.Type type;
    private final LinearLayout root;
    private final LinearLayout list;
    private final TextView status;
    private final TextView detail;
    private final ResultFileActionController actions;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);
    private TranscriptionResultRepository.Entry selected;

    /**
     * JSON、整形テキスト、録音の履歴タブを作成します。
     * @param activity 親Activity。例: {@code mainActivity}
     * @param type 表示種類。例: {@code Type.JSON}
     */
    public ResultHistoryTabView(
            @NonNull final Activity activity,
            @NonNull final TranscriptionResultRepository.Type type
    ) {
        this.activity = activity;
        this.type = type;
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        status = text(loadingMessage(type), 14);
        root.addView(status, matchWrap());

        final ScrollView listScroll = new ScrollView(activity);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list);
        root.addView(listScroll, weighted(0.48f));

        final TextView detailLabel = text(
                type == TranscriptionResultRepository.Type.AUDIO
                        ? "関連する文字起こし" : "選択した文字起こし結果",
                16);
        detailLabel.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(detailLabel, matchWrap());

        final ScrollView detailScroll = new ScrollView(activity);
        detail = text("上の一覧から結果を選択してください。", 14);
        detail.setTextIsSelectable(true);
        if (type == TranscriptionResultRepository.Type.JSON) {
            detail.setTypeface(Typeface.MONOSPACE);
        }
        detailScroll.addView(detail);
        root.addView(detailScroll, weighted(0.52f));

        actions = new ResultFileActionController(
                activity, type, this::refresh, this::reloadSelected);
    }

    /** @return MainActivityのタブへ追加するルートView。例: {@code LinearLayout} */
    @NonNull
    public View view() { return root; }

    /** 保存済み結果とメタデータを読み直し、現在の選択を可能なら維持します。 */
    public void refresh() {
        final String selectedPath = selected == null ? null : selected.file().getAbsolutePath();
        final TranscriptionResultRepository.Entry[] entries =
                TranscriptionResultRepository.list(activity, type);
        list.removeAllViews();
        selected = null;
        status.setText(entries.length + " 件");
        for (TranscriptionResultRepository.Entry entry : entries) {
            list.addView(createEntryField(entry), matchWrap());
            if (selectedPath != null && selectedPath.equals(entry.file().getAbsolutePath())) {
                selected = entry;
            }
        }
        if (entries.length == 0) {
            detail.setText("保存済みの結果はありません。");
        } else if (selected != null) {
            reloadSelected();
        }
    }

    /**
     * 録音日時・モデル・サイズ・タグと操作ボタンを縦型フィールドへまとめます。
     * @param entry 表示対象。例: {@code resultEntry}
     * @return 一覧へ追加するフィールド。例: {@code LinearLayout}
     */
    @NonNull
    private View createEntryField(@NonNull final TranscriptionResultRepository.Entry entry) {
        final LinearLayout field = new LinearLayout(activity);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        final GradientDrawable background = new GradientDrawable();
        background.setColor(0x0D000000);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), 0x33000000);
        field.setBackground(background);
        final LinearLayout.LayoutParams fieldParams = matchWrap();
        fieldParams.setMargins(0, 0, 0, dp(8));

        field.addView(text("録音日時: " + dateFormat.format(new Date(entry.recordedAtMs())), 14));
        field.addView(text("文字起こしモデル: " + entry.model(), 14));
        field.addView(text("サイズ: " + formatBytes(entry.sizeBytes()), 14));
        field.addView(text("タグ: " + entry.tag(), 14));
        field.setOnClickListener(view -> select(entry));

        final HorizontalScrollView actionScroll = new HorizontalScrollView(activity);
        final LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        if (type == TranscriptionResultRepository.Type.AUDIO) {
            actionRow.addView(actionButton("アプリで開く", view -> actions.openWithApp(entry)));
            actionRow.addView(actionButton("共有", view -> actions.share(entry)));
            actionRow.addView(actionButton("削除", view -> actions.delete(entry)));
            actionRow.addView(actionButton("テキストを閲覧", view -> actions.browseText(entry)));
        } else {
            actionRow.addView(actionButton("アプリで開く", view -> actions.openWithApp(entry)));
            actionRow.addView(actionButton("共有", view -> actions.share(entry)));
            actionRow.addView(actionButton("削除", view -> actions.delete(entry)));
            actionRow.addView(actionButton("編集", view -> actions.edit(entry)));

            if (type == TranscriptionResultRepository.Type.TEXT && entry.audioFile() != null) {
                actionRow.addView(actionButton(
                        "音声を閲覧", view -> actions.browseAudio(entry)));
            }
        }
        actionScroll.addView(actionRow);
        field.addView(actionScroll, matchWrap());
        field.setLayoutParams(fieldParams);
        return field;
    }

    /** @param entry 選択対象。例: {@code resultEntry} */
    private void select(@NonNull final TranscriptionResultRepository.Entry entry) {
        selected = entry;
        reloadSelected();
    }

    /** 選択中ファイルを、一覧とは独立した下部スクロール領域へ読み込みます。 */
    private void reloadSelected() {
        if (selected == null) {
            return;
        }
        final File target = type == TranscriptionResultRepository.Type.AUDIO
                ? selected.textFile() : selected.file();
        if (target == null) {
            detail.setText("同じ時刻データを持つ文字起こしテキストはありません。");
            return;
        }
        try {
            String value = TranscriptionResultRepository.read(target);
            if (type == TranscriptionResultRepository.Type.JSON) {
                final String trimmed = value.trim();
                value = trimmed.startsWith("[")
                        ? new JSONArray(trimmed).toString(2)
                        : new JSONObject(trimmed).toString(2);
            }
            detail.setText(value);
        } catch (Exception e) {
            detail.setText(e.getMessage());
            Toast.makeText(activity, "結果を読み込めませんでした", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 種類に応じた読込中メッセージを返します。
     * @param value タブ種類。例: {@code Type.AUDIO}
     * @return 表示文。例: {@code "録音を読み込み中"}
     */
    @NonNull
    private static String loadingMessage(@NonNull final TranscriptionResultRepository.Type value) {
        if (value == TranscriptionResultRepository.Type.JSON) {
            return "JSON結果を読み込み中";
        }
        return value == TranscriptionResultRepository.Type.AUDIO
                ? "録音を読み込み中" : "文字起こし履歴を読み込み中";
    }

    /** @param label 例: {@code "共有"} @param listener 例: {@code view -> share()} @return Button */
    @NonNull
    private Button actionButton(final String label, final View.OnClickListener listener) {
        final Button button = new Button(activity);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
    }

    /** @param bytes 例: {@code 2048} @return 読みやすいサイズ。例: {@code "2.0 KB"} */
    @NonNull
    private String formatBytes(final long bytes) {
        return bytes < 1024 ? bytes + " B" : String.format(Locale.JAPAN, "%.1f KB", bytes / 1024.0);
    }

    /** @param value 例: {@code "タグ"} @param sp 例: {@code 14} @return TextView */
    @NonNull
    private TextView text(final String value, final int sp) {
        final TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    /** @return 幅MATCH、高さWRAPのLayoutParams。例: {@code params} */
    @NonNull private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    /** @param weight 例: {@code 0.5f} @return 高さweight指定。例: {@code params} */
    @NonNull private LinearLayout.LayoutParams weighted(final float weight) {
        return new LinearLayout.LayoutParams(-1, 0, weight);
    }
    /** @param value dp。例: {@code 12} @return px。例: {@code 36} */
    private int dp(final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
