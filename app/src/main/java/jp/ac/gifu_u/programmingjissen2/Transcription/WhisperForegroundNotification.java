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
                CHANNEL_ID, "Whisper バックグラウンド処理",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("バックグラウンド録音とファイル文字起こし");
        final NotificationManager manager = service.getSystemService(NotificationManager.class);
        if (manager != null) { manager.createNotificationChannel(channel); }
    }

    /**
     * Foreground通知を開始します。
     * @param title 通知タイトル。例: {@code "Whisper 録音"}
     * @param text 通知本文。例: {@code "録音中"}
     * @param recording 録音中ならtrue。例: {@code true}
     * @param foregroundServiceTypes Service種別bit mask。例: {@code ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE}
     */
    void start(
            final String title,
            final String text,
            final boolean recording,
            final int foregroundServiceTypes
    ) {
        final Notification notification = build(title, text, recording, false);
        service.startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes);
    }

    /**
     * 既存通知を更新します。
     * @param text 本文。例: {@code "こんにちは"}
     * @param recording 録音中ならtrue。例: {@code true}
     */
    void update(final String text, final boolean recording) {
        update("Whisper 録音", text, recording, false);
    }

    /**
     * ファイル文字起こし用のForeground通知を開始します。
     * @param text 通知本文。例: {@code "音声ファイルを読み込み中"}
     * @param foregroundServiceTypes Service種別。例: {@code ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC}
     * 戻り値と例外はありません。
     */
    void startFileTranscription(final String text, final int foregroundServiceTypes) {
        final Notification notification =
                build("Whisper ファイル文字起こし", text, false, true);
        service.startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes);
    }

    /**
     * ファイル文字起こし通知を更新します。
     * @param text 通知本文。例: {@code "文字起こし中"}
     * 戻り値と例外はありません。
     */
    void updateFileTranscription(final String text) {
        update("Whisper ファイル文字起こし", text, false, true);
    }

    /**
     * 指定モードの既存通知を更新します。
     * @param title 通知タイトル。例: {@code "Whisper 録音"}
     * @param text 本文。例: {@code "録音中"}
     * @param recording 録音中ならtrue。例: {@code true}
     * @param fileTranscribing ファイル文字起こし中ならtrue。例: {@code false}
     * 戻り値と例外はありません。
     */
    private void update(
            final String title,
            final String text,
            final boolean recording,
            final boolean fileTranscribing
    ) {
        final NotificationManager manager =
                (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(
                    NOTIFICATION_ID,
                    build(title, text, recording, fileTranscribing));
        }
    }

    /**
     * 現在状態に対応する通知を作成します。
     * @param title タイトル。例: {@code "Whisper 録音"}
     * @param text 本文。例: {@code "録音中"}
     * @param recording 録音中ならtrue。例: {@code true}
     * @param fileTranscribing ファイル文字起こし中ならtrue。例: {@code false}
     * @return 操作ボタン付き通知。例: {@code Notification}
     */
    @NonNull
    private Notification build(
            final String title,
            final String text,
            final boolean recording,
            final boolean fileTranscribing
    ) {
        final PendingIntent contentIntent = PendingIntent.getActivity(
                service, 0, new Intent(service, MainActivity.class), pendingIntentFlags());
        final String action = fileTranscribing
                ? BackgroundWhisperService.ACTION_STOP_FILE_TRANSCRIPTION
                : (recording
                        ? BackgroundWhisperService.ACTION_STOP
                        : BackgroundWhisperService.ACTION_START);
        final PendingIntent recordIntent = PendingIntent.getService(
                service, 1,
                new Intent(service, BackgroundWhisperService.class).setAction(action),
                pendingIntentFlags());
        final String displayText = text == null || text.isEmpty()
                ? "最新の文字起こしはまだありません" : text;
        return new NotificationCompat.Builder(service, CHANNEL_ID) // バックグラウンドで実行するために最低限必要
                .setSmallIcon(R.drawable.appicon_bygbt) //アイコンを設定
                .setContentTitle(title) // 通知タイトルを設定
                .setContentText(displayText) //通知テキストを設定
                .setStyle(new NotificationCompat.BigTextStyle().bigText(displayText)) //スタイルを設定
                .setContentIntent(contentIntent) //
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                        0,
                        fileTranscribing ? "処理を停止" : (recording ? "録音停止" : "録音開始"),
                        recordIntent) // 処理停止ボタンを設定
                .build();
    }

    /** @return Android版に適したPendingIntent flags。例: {@code PendingIntent.FLAG_IMMUTABLE} */
    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
