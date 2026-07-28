package jp.ac.gifu_u.programmingjissen2.Transcription.Prompt;

import androidx.annotation.NonNull;

import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 文字起こし結果から、次回initial_promptへ渡す直近の単語一覧を作成します。 */
public final class PreviousContextWordExtractor {
    /** 各推論スレッドだけが使用する一時コレクションです。 */
    private static final ThreadLocal<ScratchBuffers> SCRATCH_BUFFERS =
            ThreadLocal.withInitial(ScratchBuffers::new);

    private PreviousContextWordExtractor() {
    }

    /**
     * 文章をUnicode単語境界で分割し、句読点を除いた直近の重複しない単語を返します。
     *
     * @param text 文字起こし結果。例: {@code "今日は岐阜大学でWhisperを使います。"}
     * @param languageCode Whisper言語コード。例: {@code "ja"}。自動検出時は{@code "auto"}
     * @param maxWords 最大単語数。例: {@code 24}
     * @param maxCodePoints 空白を含む最大文字数。例: {@code 100}
     * @return 空白区切りの単語一覧。例: {@code "今日 は 岐阜大学 で Whisper を 使い ます"}
     *         。入力が空または上限が0以下なら空文字を返し、例外はありません
     */
    @NonNull
    public static String extractRecentWords(
            final String text,
            final String languageCode,
            final int maxWords,
            final int maxCodePoints
    ) {
        if (text == null || text.trim().isEmpty() || maxWords <= 0 || maxCodePoints <= 0) {
            return "";
        }
        final String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        final BreakIterator iterator = BreakIterator.getWordInstance(
                selectLocale(normalized, languageCode)
        );
        iterator.setText(normalized);

        final ScratchBuffers scratch = SCRATCH_BUFFERS.get();
        scratch.clear();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            final String candidate = normalized.substring(start, end).trim();
            if (containsLetterOrDigit(candidate)) {
                scratch.words.add(candidate);
            }
        }
        return selectRecentUniqueWords(
                scratch.words,
                maxWords,
                maxCodePoints,
                scratch.selected,
                scratch.seen
        );
    }

    /**
     * 末尾から重複しない語を上限内で選び、元の出現順へ戻します。
     *
     * @param words 抽出済み単語。例: {@code List.of("Whisper", "を", "Whisper")}
     * @param maxWords 最大単語数。例: {@code 24}
     * @param maxCodePoints 最大文字数。例: {@code 100}
     * @param selectedOutput 選択結果の一時出力先。例: {@code new ArrayList<>()}
     * @param seenOutput 重複判定用の再利用Set。例: {@code new HashSet<>()}
     * @return 選択した単語列。例: {@code "を Whisper"}。例外はありません
     */
    @NonNull
    private static String selectRecentUniqueWords(
            @NonNull final List<String> words,
            final int maxWords,
            final int maxCodePoints,
            @NonNull final ArrayList<String> selectedOutput,
            @NonNull final Set<String> seenOutput
    ) {
        selectedOutput.clear();
        seenOutput.clear();
        int usedCodePoints = 0;
        for (int index = words.size() - 1;
             index >= 0 && selectedOutput.size() < maxWords;
             index--) {
            final String word = words.get(index);
            final String comparisonKey = word.toLowerCase(Locale.ROOT);
            if (!seenOutput.add(comparisonKey)) {
                continue;
            }
            final int wordLength = word.codePointCount(0, word.length());
            final int requiredLength = wordLength + (selectedOutput.isEmpty() ? 0 : 1);
            if (requiredLength > maxCodePoints - usedCodePoints) {
                continue;
            }
            selectedOutput.add(word);
            usedCodePoints += requiredLength;
        }
        Collections.reverse(selectedOutput);
        return String.join(" ", selectedOutput);
    }

    /**
     * 分割片に文字または数字が含まれるか判定します。
     *
     * @param value 分割片。例: {@code "Whisper"}
     * @return 単語として使える場合true。例: {@code true}。例外はありません
     */
    private static boolean containsLetterOrDigit(@NonNull final String value) {
        return value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    /**
     * 単語分割へ使用するLocaleを決めます。
     *
     * @param text 正規化済み本文。例: {@code "岐阜大学"}
     * @param languageCode 言語コード。例: {@code "ja"}
     * @return 分割用Locale。例: {@code Locale.JAPANESE}。例外はありません
     */
    @NonNull
    private static Locale selectLocale(@NonNull final String text, final String languageCode) {
        if (containsJapaneseScript(text)) {
            return Locale.JAPANESE;
        }
        if (languageCode == null || languageCode.isBlank() || "auto".equals(languageCode)) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(languageCode);
    }

    /**
     * 日本語の文字種を含むか判定します。
     *
     * @param text 判定対象。例: {@code "Whisperを使う"}
     * @return 漢字・ひらがな・カタカナを含む場合true。例: {@code true}
     */
    private static boolean containsJapaneseScript(@NonNull final String text) {
        return text.codePoints().anyMatch(codePoint -> {
            final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA;
        });
    }

    /** ThreadLocalで安全に再利用する単語抽出用バッファです。 */
    private static final class ScratchBuffers {
        private final ArrayList<String> words = new ArrayList<>(32);
        private final ArrayList<String> selected = new ArrayList<>(24);
        private final Set<String> seen = new HashSet<>(32);

        /** 全バッファを空にします。引数・返り値・例外はありません。 */
        private void clear() {
            words.clear();
            selected.clear();
            seen.clear();
        }
    }
}
