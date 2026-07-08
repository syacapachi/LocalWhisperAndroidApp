package jp.ac.gifu_u.programmingjissen2.TranscriptionText;

import androidx.annotation.NonNull;

import java.util.List;

import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;

/** 時間と本文を読みやすいプレーンテキストへ整形する class です。 */
public final class TranscriptionTextFormatter {
    private TranscriptionTextFormatter() {
    }

    /**
     * 複数の文字起こし item を時刻付きテキストへ整形します。
     *
     * @param items 時間と本文の一覧。例: {@code List.of(new TranscriptionTextItem(1000, "こんにちは"))}
     * @return 整形済みテキスト。例: {@code "[00:01.000] こんにちは\n"}
     */
    @NonNull
    public static String format(@NonNull final List<TranscriptionTextItem> items) {
        try(PooledStringBuilder builder = ScopableUtility.getBuilder()) {
            for (TranscriptionTextItem item : items) {
                builder.append(formatLine(item.timeMs(), item.text()));
            }
            return builder.toString();
        }
    }

    /**
     * 1件の文字起こしを時刻付きテキストへ整形します。
     *
     * @param item 時間と本文。例: {@code new TranscriptionTextItem(1000, "こんにちは")}
     * @return 整形済み1行。例: {@code "[00:01.000] こんにちは\n"}
     */
    @NonNull
    public static String format(@NonNull final TranscriptionTextItem item) {
        return formatLine(item.timeMs(), item.text());
    }

    /**
     * 開始時刻と本文を時刻付き1行へ整形します。
     *
     * @param timeMs 開始時刻。例: {@code 1234}
     * @param text 本文。例: {@code "こんにちは"}
     * @return 整形済み1行。例: {@code "[00:01.234] こんにちは\n"}
     */
    @NonNull
    public static String formatLine(final long timeMs, final String text) {
        try(PooledStringBuilder builder = ScopableUtility.getBuilder()) {
            builder.append("[")
                    .append(formatTime(timeMs))
                    .append("] ")
                    .append(text == null ? "" : text.trim())
                    .append("\n");
            return builder.toString();
        }
    }

    /**
     * ミリ秒を mm:ss.SSS 形式へ変換します。
     *
     * @param timeMs ミリ秒。例: {@code 1234}
     * @return 時刻文字列。例: {@code "00:01.234"}
     */
    @NonNull
    public static String formatTime(final long timeMs) {
        final long safeMs = Math.max(0, timeMs);
        final long minutes = safeMs / 60_000;
        final long seconds = (safeMs % 60_000) / 1_000;
        final long millis = safeMs % 1_000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
