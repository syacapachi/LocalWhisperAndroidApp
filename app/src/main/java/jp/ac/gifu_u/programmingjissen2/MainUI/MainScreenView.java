package jp.ac.gifu_u.programmingjissen2.MainUI;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;

import jp.ac.gifu_u.programmingjissen2.ResultUI.ResultHistoryTabView;
import jp.ac.gifu_u.programmingjissen2.ResultUI.TranscriptionResultRepository;

/** アプリ名・設定ボタンと、設定に応じてデバッグ結果を加えるコンテンツタブ画面です。 */
public final class MainScreenView {
    private final LinearLayout root;
    private final Button settingsButton;
    private final QuickStartTabView quickStart;
    private final ResultHistoryTabView jsonHistory;
    private final ResultHistoryTabView textHistory;
    private final ResultHistoryTabView audioHistory;
    private final TabLayout tabs;
    private final View[] pages;
    private boolean debugHistoryVisible;

    /** @param activity MainActivity。例: {@code this} */
    public MainScreenView(@NonNull final Activity activity) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 8), dp(activity, 6));
        final TextView appName = new TextView(activity);
        appName.setText("LocalScribe");
        appName.setTextSize(20);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(appName, new LinearLayout.LayoutParams(0, -2, 1f));
        settingsButton = new Button(activity);
        settingsButton.setText("⚙");
        settingsButton.setTextSize(22);
        settingsButton.setContentDescription("設定を開く");
        header.addView(settingsButton, new LinearLayout.LayoutParams(
                dp(activity, 56), dp(activity, 48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        tabs = new TabLayout(activity);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        quickStart = new QuickStartTabView(activity);
        jsonHistory = new ResultHistoryTabView(
                activity, TranscriptionResultRepository.Type.JSON);
        textHistory = new ResultHistoryTabView(
                activity, TranscriptionResultRepository.Type.TEXT);
        audioHistory = new ResultHistoryTabView(
                activity, TranscriptionResultRepository.Type.AUDIO);
        pages = new View[]{
                quickStart.view(), jsonHistory.view(), textHistory.view(), audioHistory.view()
        };
        final FrameLayout pageHost = new FrameLayout(activity);
        for (int index = 0; index < pages.length; index++) {
            pages[index].setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            pageHost.addView(pages[index], new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        final TextView transcriptionNotice = new TextView(activity);
        transcriptionNotice.setText(
                "このアプリは完全な文字起こしを提供するものではありません。"
                        + "重要な音声は自分で確認するようにしてください。");
        transcriptionNotice.setTextSize(12);
        transcriptionNotice.setGravity(Gravity.CENTER);
        transcriptionNotice.setPadding(
                dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
        transcriptionNotice.setBackgroundColor(0x0D000000);
        transcriptionNotice.setElevation(dp(activity, 4));
        root.addView(transcriptionNotice, new LinearLayout.LayoutParams(-1, -2));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                final Object tag = tab.getTag();
                final int pageIndex = tag instanceof Integer ? (Integer) tag : 0;
                showPage(pageIndex);
                if (pageIndex == 1) {
                    jsonHistory.refresh();
                } else if (pageIndex == 2) {
                    textHistory.refresh();
                } else if (pageIndex == 3) {
                    audioHistory.refresh();
                }
            }
            @Override public void onTabUnselected(@NonNull final TabLayout.Tab tab) { }
            @Override public void onTabReselected(@NonNull final TabLayout.Tab tab) {
                onTabSelected(tab);
            }
        });
        setDebugHistoryVisible(false);
    }

    /** @return MainActivityへ設定するルートView。例: {@code LinearLayout} */
    @NonNull public View view() { return root; }
    /** @return 右上の設定Button。例: {@code settingsButton} */
    @NonNull public Button settingsButton() { return settingsButton; }
    /** @return クイックスタートUI。例: {@code QuickStartTabView} */
    @NonNull public QuickStartTabView quickStart() { return quickStart; }

    /**
     * デバッグJSON履歴タブを右端へ追加または削除し、クイックスタートを表示します。
     * @param visible JSONデバッグタブを表示するならtrue。例: {@code true}
     */
    public void setDebugHistoryVisible(final boolean visible) {
        if (tabs.getTabCount() > 0 && debugHistoryVisible == visible) {
            return;
        }
        debugHistoryVisible = visible;
        tabs.removeAllTabs();
        tabs.addTab(tabs.newTab().setText("クイックスタート").setTag(0), false);
        tabs.addTab(tabs.newTab().setText("文字起こし履歴").setTag(2), false);
        tabs.addTab(tabs.newTab().setText("録音").setTag(3), false);
        if (visible) {
            tabs.addTab(tabs.newTab()
                    .setText("文字起こしJSON結果（デバッグ）")
                    .setTag(1), false);
        }
        showPage(0);
        final TabLayout.Tab quickStartTab = tabs.getTabAt(0);
        if (quickStartTab != null) {
            quickStartTab.select();
        }
    }

    /** JSON、整形テキスト、録音の各一覧を再読み込みします。 */
    public void refreshHistories() {
        jsonHistory.refresh();
        textHistory.refresh();
        audioHistory.refresh();
    }

    /**
     * 指定した固定ページだけを表示します。
     * @param pageIndex pages内の位置。例: {@code 2}
     */
    private void showPage(final int pageIndex) {
        for (int index = 0; index < pages.length; index++) {
            pages[index].setVisibility(index == pageIndex ? View.VISIBLE : View.GONE);
        }
    }

    /** @param activity 例: {@code mainActivity} @param value dp。例: {@code 16} @return px */
    private static int dp(@NonNull final Activity activity, final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
