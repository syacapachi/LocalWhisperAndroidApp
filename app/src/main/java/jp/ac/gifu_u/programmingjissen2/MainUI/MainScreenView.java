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

/** アプリ名・設定ボタンと三つのコンテンツタブを持つメイン画面です。 */
public final class MainScreenView {
    private final LinearLayout root;
    private final Button settingsButton;
    private final QuickStartTabView quickStart;
    private final ResultHistoryTabView jsonHistory;
    private final ResultHistoryTabView textHistory;

    /** @param activity MainActivity。例: {@code this} */
    public MainScreenView(@NonNull final Activity activity) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 8), dp(activity, 6));
        final TextView appName = new TextView(activity);
        appName.setText("Whisper文字起こしアプリ");
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

        final TabLayout tabs = new TabLayout(activity);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.addTab(tabs.newTab().setText("クイックスタート"));
        tabs.addTab(tabs.newTab().setText("文字起こしJSON結果（デバッグ）"));
        tabs.addTab(tabs.newTab().setText("文字起こし履歴"));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, -2));

        quickStart = new QuickStartTabView(activity);
        jsonHistory = new ResultHistoryTabView(
                activity, TranscriptionResultRepository.Type.JSON);
        textHistory = new ResultHistoryTabView(
                activity, TranscriptionResultRepository.Type.TEXT);
        final View[] pages = {quickStart.view(), jsonHistory.view(), textHistory.view()};
        final FrameLayout pageHost = new FrameLayout(activity);
        for (int index = 0; index < pages.length; index++) {
            pages[index].setVisibility(index == 0 ? View.VISIBLE : View.GONE);
            pageHost.addView(pages[index], new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull final TabLayout.Tab tab) {
                for (int index = 0; index < pages.length; index++) {
                    pages[index].setVisibility(index == tab.getPosition()
                            ? View.VISIBLE : View.GONE);
                }
                if (tab.getPosition() == 1) {
                    jsonHistory.refresh();
                } else if (tab.getPosition() == 2) {
                    textHistory.refresh();
                }
            }
            @Override public void onTabUnselected(@NonNull final TabLayout.Tab tab) { }
            @Override public void onTabReselected(@NonNull final TabLayout.Tab tab) {
                onTabSelected(tab);
            }
        });
    }

    /** @return MainActivityへ設定するルートView。例: {@code LinearLayout} */
    @NonNull public View view() { return root; }
    /** @return 右上の設定Button。例: {@code settingsButton} */
    @NonNull public Button settingsButton() { return settingsButton; }
    /** @return クイックスタートUI。例: {@code QuickStartTabView} */
    @NonNull public QuickStartTabView quickStart() { return quickStart; }

    /** JSONと整形テキストの両一覧を再読み込みします。 */
    public void refreshHistories() {
        jsonHistory.refresh();
        textHistory.refresh();
    }

    /** @param activity 例: {@code mainActivity} @param value dp。例: {@code 16} @return px */
    private static int dp(@NonNull final Activity activity, final int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
