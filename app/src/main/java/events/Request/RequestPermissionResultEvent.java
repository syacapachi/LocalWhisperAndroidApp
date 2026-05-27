package events.Request;

import androidx.annotation.NonNull;

/**
 * Activityにきた結果をそのままイベントとして発行するためのラッパークラス
 * @param requestCode
 * @param permissions
 * @param grantResults
 */
public record RequestPermissionResultEvent(int requestCode, @NonNull String[] permissions,@NonNull int[] grantResults) {
}
