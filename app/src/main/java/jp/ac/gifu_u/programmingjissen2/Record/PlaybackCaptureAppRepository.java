package jp.ac.gifu_u.programmingjissen2.Record;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 再生音声キャプチャの対象候補となる起動可能アプリを列挙します。 */
public final class PlaybackCaptureAppRepository {
    private PlaybackCaptureAppRepository() { }

    /**
     * Android 10以降を対象にした、同一ユーザーの起動可能アプリを読み込みます。
     *
     * <p>対象アプリは標準でキャプチャ可能ですが、manifestや各プレイヤーが明示的に
     * 拒否している場合はOSによって無音化されます。</p>
     *
     * @param packageManager PackageManager。例: {@code activity.getPackageManager()}
     * @return ラベル順の候補。例: {@code [new CaptureTargetApp("YouTube", "com.google.android.youtube", 10123)]}
     * @throws SecurityException 端末ポリシーによりアプリ情報取得が拒否された場合
     */
    @NonNull
    public static List<CaptureTargetApp> load(@NonNull final PackageManager packageManager) {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL
        );
        final List<CaptureTargetApp> apps = new ArrayList<>();
        final Set<String> seenPackages = new HashSet<>();

        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            final ApplicationInfo application = info.activityInfo.applicationInfo;
            if (application.targetSdkVersion < Build.VERSION_CODES.Q
                    || !seenPackages.add(application.packageName)) {
                continue;
            }
            apps.add(new CaptureTargetApp(
                    String.valueOf(info.loadLabel(packageManager)),
                    application.packageName,
                    application.uid
            ));
        }
        apps.sort(Comparator.comparing(
                CaptureTargetApp::label,
                String.CASE_INSENSITIVE_ORDER
        ));
        return apps;
    }
}
