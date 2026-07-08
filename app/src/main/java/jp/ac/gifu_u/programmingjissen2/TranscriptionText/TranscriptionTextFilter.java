package jp.ac.gifu_u.programmingjissen2.TranscriptionText;

import androidx.annotation.NonNull;

/** 文字起こしテキストの後処理フィルターです。 */
public final class TranscriptionTextFilter {
    /**
     * 入力テキストをフィルターして返します。
     *
     * @param input 整形済みテキスト。例: {@code "[00:00.000] こんにちは\n"}
     * @return フィルター後テキスト。例: {@code "[00:00.000] こんにちは\n"}
     */
    @NonNull
    public static String filter(final String input) {
        return input == null ? "" : input;
    }
}
