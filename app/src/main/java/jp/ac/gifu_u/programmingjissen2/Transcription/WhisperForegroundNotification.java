package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import jp.ac.gifu_u.programmingjissen2.MainActivity;
import jp.ac.gifu_u.programmingjissen2.R;

/** Whisper Foreground Serviceの通知生成と更新を担当します。 */
final class WhisperForegroundNotification {
    private static final String CHANNEL_ID = "whisper_recording";
    private static final int NOTIFICATION_ID = 2100;
    private final Service service;

    /**
     * 通知controllerを作成します。
     * @param service 通知を所有するService。例: {@code backgroundWhisperService}
     */
    WhisperForegroundNotification(@NonNull final Service service) {
        this.service = service;
    }

    /** Android 8以降用の通知チャネルを作成します。 */
    void createChannel() {
        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Whisper 録音", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("バックグラウンド録音と Whisper 文字起こし");
        final NotificationManager manager = service.getSystemService(NotificationManager.class);
        if (manager != null) { manager.createNotificationChannel(channel); }
    }

    /**
     * Foreground通知を開始します。
     * @param title 通知タイトル。例: {@code "Whisper 録音"}
     * @param text 通知本文。例: {@code "録音中"}
     * @param recording 録音中ならtrue。例: {@code true}
     */
    void start(final String title, final String text, final boolean recording) {
        final Notification notification = build(title, text, recording);
        service.startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    }

    /**
     * 既存通知を更新します。
     * @param text 本文。例: {@code "こんにちは"}
     * @param recording 録音中ならtrue。例: {@code true}
     */
    void update(final String text, final boolean recording) {
        final NotificationManager manager =
                (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, build("Whisper 録音", text, recording));
        }
    }

    /**
     * 現在状態に対応する通知を作成します。
     * @param title タイトル。例: {@code "Whisper 録音"}
     * @param text 本文。例: {@code "録音中"}
     * @param recording 録音中ならtrue。例: {@code true}
     * @return 操作ボタン付き通知。例: {@code Notification}
     */
    @NonNull
    private Notification build(final String title, final String text, final boolean recording) {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                service, 0, new Intent(service, MainActivity.class), pendingIntentFlags());
        final String action = recording
                ? BackgroundWhisperService.ACTION_STOP
                : BackgroundWhisperService.ACTION_START;
        final PendingIntent recordIntent = PendingIntent.getService(
                service, 1,
                new Intent(service, BackgroundWhisperService.class).setAction(action),
                pendingIntentFlags());
        final String displayText = text == null || text.isEmpty()
                ? "最新の文字起こしはまだありません" : text;
        return new NotificationCompat.Builder(service, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(displayText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(displayText))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, recording ? "録音停止" : "録音開始", recordIntent)
                .build();
    }

    /** @return Android版に適したPendingIntent flags。例: {@code PendingIntent.FLAG_IMMUTABLE} */
    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
