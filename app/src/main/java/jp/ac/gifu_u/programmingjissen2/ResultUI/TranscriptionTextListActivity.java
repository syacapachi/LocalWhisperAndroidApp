package jp.ac.gifu_u.programmingjissen2.ResultUI;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import Utils.StringPool.StringBufferBuilderPool;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextRepository;

/** 保存済みの整形済み文字起こしテキストを表示する画面です。 */
public class TranscriptionTextListActivity extends AppCompatActivity {
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    private LinearLayout listContainer;
    private TextView statusText;
    private TextView previewText;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("文字起こしテキスト");
        setContentView(createContentView());
        refreshFileList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFileList();
    }

    /**
     * 画面の View 階層を作成します。
     *
     * @return 画面ルート View。例: {@code ScrollView}
     */
    @NonNull
    private View createContentView() {
        final ScrollView scrollView = new ScrollView(this);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        root.addView(textView("文字起こしテキスト", 24));
        statusText = textView("", 14);
        root.addView(statusText);

        final Button refreshButton = new Button(this);
        refreshButton.setText("更新");
        refreshButton.setOnClickListener((view) -> refreshFileList());
        root.addView(refreshButton);

        root.addView(textView("ファイル一覧", 18));
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        root.addView(textView("内容", 18));
        previewText = textView("ファイルを選択するとテキストを表示します。", 14);
        previewText.setTextIsSelectable(true);
        root.addView(previewText);

        final Button closeButton = new Button(this);
        closeButton.setText("閉じる");
        closeButton.setOnClickListener((view) -> finish());
        root.addView(closeButton);
        return scrollView;
    }

    /**
     * 保存済みテキストファイル一覧を再読み込みします。
     */
    private void refreshFileList() {
        final File[] files = TranscriptionTextRepository.listTextFiles(this);
        listContainer.removeAllViews();
        if (files.length == 0) {
            statusText.setText("保存済みの文字起こしテキストはまだありません。");
            previewText.setText("録音停止または音声ファイル文字起こし完了後に保存されます。");
            return;
        }

        statusText.setText(StringBufferBuilderPool.Join("", files.length, " 件のテキストがあります。"));
        for (File file : files) {
            listContainer.addView(createFileButton(file));
        }
    }

    /**
     * テキストファイルを開くボタンを作成します。
     *
     * @param file 表示対象ファイル。例: {@code new File(..., "a.txt")}
     * @return ファイルを開く Button。例: {@code Button}
     */
    @NonNull
    private View createFileButton(@NonNull final File file) {
        final Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(formatFileLabel(file));
        button.setOnClickListener((view) -> openFile(file));
        return button;
    }

    /**
     * 指定ファイルを読み込んでプレビューへ表示します。
     *
     * @param file 読み込むファイル。例: {@code new File(..., "a.txt")}
     */
    private void openFile(@NonNull final File file) {
        try {
            previewText.setText(TranscriptionTextRepository.readText(file));
        } catch (IOException e) {
            Toast.makeText(this, "テキストを読み込めませんでした", Toast.LENGTH_SHORT).show();
            previewText.setText(e.getMessage());
        }
    }

    /**
     * ファイル名、更新日時、サイズを一覧用ラベルへ変換します。
     *
     * @param file ラベル化するファイル。例: {@code new File(..., "a.txt")}
     * @return 一覧用ラベル。例: {@code "a.txt\n2026/07/08 12:00:00 / 100 bytes"}
     */
    @NonNull
    private String formatFileLabel(@NonNull final File file) {
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

    /**
     * TextView を作成します。
     *
     * @param text 表示文字列。例: {@code "内容"}
     * @param sp 文字サイズsp。例: {@code 14}
     * @return 作成した TextView。例: {@code TextView}
     */
    @NonNull
    private TextView textView(final String text, final int sp) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    /**
     * dp値をpx値へ変換します。
     *
     * @param value dp値。例: {@code 20}
     * @return px値。例: {@code 60}
     */
    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
