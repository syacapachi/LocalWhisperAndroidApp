package jp.ac.gifu_u.programmingjissen2;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import Utils.StringPool.StringBufferBuilderPool;

/** 保存済みの文字起こし JSON を一覧表示し、内容確認と共有を行う画面です。 */
public class TranscriptionListActivity extends AppCompatActivity {
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView previewText;
    private Button latestOpenButton;
    private Button latestShareButton;
    private File latestFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("文字起こし履歴");
        setContentView(createContentView());
        refreshFileList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFileList();
    }

    /** 履歴画面の View 階層を作成します。 */
    private View createContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = titleText("文字起こし履歴");
        root.addView(title);

        statusText = descriptionText("");
        root.addView(statusText);

        LinearLayout latestRow = new LinearLayout(this);
        latestRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(latestRow, fullWidthParams());

        latestOpenButton = new Button(this);
        latestOpenButton.setText("最新を開く");
        latestOpenButton.setOnClickListener((view) -> openLatestFile());
        latestRow.addView(latestOpenButton, weightedButtonParams());

        latestShareButton = new Button(this);
        latestShareButton.setText("最新を共有");
        latestShareButton.setOnClickListener((view) -> shareLatestFile());
        latestRow.addView(latestShareButton, weightedButtonParams());

        Button refreshButton = new Button(this);
        refreshButton.setText("更新");
        refreshButton.setOnClickListener((view) -> refreshFileList());
        root.addView(refreshButton, fullWidthParams());

        root.addView(sectionText("ファイル一覧"));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, fullWidthParams());

        root.addView(sectionText("内容"));
        previewText = descriptionText("ファイルを選択すると JSON の内容を表示します。");
        previewText.setTextIsSelectable(true);
        previewText.setTypeface(Typeface.MONOSPACE);
        root.addView(previewText, fullWidthParams());

        Button closeButton = new Button(this);
        closeButton.setText("閉じる");
        closeButton.setOnClickListener((view) -> finish());
        root.addView(closeButton, fullWidthParams());

        return scrollView;
    }

    /** transcriptions ディレクトリを読み直して、ファイル一覧を新しい順に表示します。 */
    private void refreshFileList() {
        File[] files = listTranscriptionFiles();
        latestFile = files.length == 0 ? null : files[0];
        latestOpenButton.setEnabled(latestFile != null);
        latestShareButton.setEnabled(latestFile != null);
        listContainer.removeAllViews();

        if (files.length == 0) {
            statusText.setText("保存済みの文字起こし JSON はまだありません。");
            previewText.setText("録音を停止すると JSON が保存されます。");
            return;
        }

        statusText.setText(StringBufferBuilderPool.Join(
                "",
                files.length,
                " 件の JSON ファイルがあります。最新: ",
                latestFile.getName()
        ));

        for (File file : files) {
            listContainer.addView(createFileRow(file));
        }
    }

    /** 保存済み JSON ファイルを取得し、更新日時の新しい順に並べます。 */
    private File[] listTranscriptionFiles() {
        File directory = new File(getFilesDir(), TranscriptionJsonWriter.DIRECTORY_NAME);
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    /** 1 ファイル分の「開く」「共有」行を作成します。 */
    private View createFileRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(8));

        Button openButton = new Button(this);
        openButton.setText(formatFileLabel(file));
        openButton.setAllCaps(false);
        openButton.setOnClickListener((view) -> openFile(file));
        row.addView(openButton, weightedButtonParams());

        Button shareButton = new Button(this);
        shareButton.setText("共有");
        shareButton.setOnClickListener((view) -> shareFile(file));
        row.addView(shareButton, smallButtonParams());

        return row;
    }

    /** 最新ファイルをプレビュー欄に表示します。 */
    private void openLatestFile() {
        if (latestFile == null) {
            Toast.makeText(this, "保存済みファイルがありません", Toast.LENGTH_SHORT).show();
            return;
        }
        openFile(latestFile);
    }

    /** 最新ファイルを Android の共有シートへ渡します。 */
    private void shareLatestFile() {
        if (latestFile == null) {
            Toast.makeText(this, "保存済みファイルがありません", Toast.LENGTH_SHORT).show();
            return;
        }
        shareFile(latestFile);
    }

    /** 指定された JSON ファイルを読み込み、見やすく整形して表示します。 */
    private void openFile(File file) {
        try {
            previewText.setText(StringBufferBuilderPool.Join(
                    "\n\n",
                    formatFileLabel(file),
                    prettyJson(readTextFile(file))
            ));
        } catch (IOException e) {
            Toast.makeText(this, "JSON を読み込めませんでした", Toast.LENGTH_SHORT).show();
            previewText.setText(e.getMessage());
        }
    }

    /** 指定された JSON ファイルを FileProvider の Uri として共有します。 */
    private void shareFile(File file) {
        Uri uri = FileProvider.getUriForFile(
                this,
                StringBufferBuilderPool.Join("", getPackageName(), ".fileprovider"),
                file
        );
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContentResolver(), file.getName(), uri));
        startActivity(Intent.createChooser(intent, "文字起こし JSON を共有"));
    }

    /** UTF-8 のテキストファイルを読み込みます。 */
    private String readTextFile(File file) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    /** JSON 文字列をインデント付きで表示できる形へ整えます。 */
    private String prettyJson(String rawJson) {
        String trimmed = rawJson.trim();
        try {
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed).toString(2);
            }
            return new JSONObject(trimmed).toString(2);
        } catch (JSONException e) {
            return rawJson;
        }
    }

    /** ファイル名、更新日時、サイズを一覧用の文字列にします。 */
    private String formatFileLabel(File file) {
        return StringBufferBuilderPool.Join(
                "",
                file.getName(),
                "\n",
                dateFormat.format(new Date(file.lastModified())),
                " / ",
                file.length(),
                " bytes"
        );
    }

    private TextView titleText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(24);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView sectionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(6));
        return view;
    }

    private TextView descriptionText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams smallButtonParams() {
        return new LinearLayout.LayoutParams(
                dp(96),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
