package Whisper;

import androidx.annotation.NonNull;

import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;

/**
 * Whisper.cpp を Java/Kotlin 側から呼び出すための JNI ブリッジです。
 *
 * <p>このクラスで扱う {@code long context}、{@code long state}、{@code long vadContext}、
 * {@code long vadSegments} は native 側のポインタを表すハンドルです。生成したハンドルは、
 * 使い終わったら対応する {@code free...()} 関数で解放してください。</p>
 *
 * <p>音声データは Whisper.cpp の標準入力と同じく、基本的に 16kHz・モノラル・
 * {@code -1.0f} から {@code 1.0f} に正規化された PCM を想定しています。</p>
 */
public class WhisperBridge {

    /** サンプリング方式: greedy search。高速で、通常の文字起こし向けです。 */
    public static final int SAMPLING_GREEDY = 0;

    /** サンプリング方式: beam search。候補を広く探索しますが、greedy より重くなります。 */
    public static final int SAMPLING_BEAM_SEARCH = 1;

    /** DTW token timestamp 用の aheads preset: 使用しません。 */
    public static final int AHEADS_NONE = 0;

    /** DTW token timestamp 用の aheads preset: 上位 N 個の attention head を使います。 */
    public static final int AHEADS_N_TOP_MOST = 1;

    /** DTW token timestamp 用の aheads preset: カスタム設定を使います。 */
    public static final int AHEADS_CUSTOM = 2;

    /** DTW token timestamp 用の aheads preset: tiny.en モデル向け。 */
    public static final int AHEADS_TINY_EN = 3;

    /** DTW token timestamp 用の aheads preset: tiny モデル向け。 */
    public static final int AHEADS_TINY = 4;

    /** DTW token timestamp 用の aheads preset: base.en モデル向け。 */
    public static final int AHEADS_BASE_EN = 5;

    /** DTW token timestamp 用の aheads preset: base モデル向け。 */
    public static final int AHEADS_BASE = 6;

    /** DTW token timestamp 用の aheads preset: small.en モデル向け。 */
    public static final int AHEADS_SMALL_EN = 7;

    /** DTW token timestamp 用の aheads preset: small モデル向け。 */
    public static final int AHEADS_SMALL = 8;

    /** DTW token timestamp 用の aheads preset: medium.en モデル向け。 */
    public static final int AHEADS_MEDIUM_EN = 9;

    /** DTW token timestamp 用の aheads preset: medium モデル向け。 */
    public static final int AHEADS_MEDIUM = 10;

    /** DTW token timestamp 用の aheads preset: large-v1 モデル向け。 */
    public static final int AHEADS_LARGE_V1 = 11;

    /** DTW token timestamp 用の aheads preset: large-v2 モデル向け。 */
    public static final int AHEADS_LARGE_V2 = 12;

    /** DTW token timestamp 用の aheads preset: large-v3 モデル向け。 */
    public static final int AHEADS_LARGE_V3 = 13;

    /** DTW token timestamp 用の aheads preset: large-v3-turbo モデル向け。 */
    public static final int AHEADS_LARGE_V3_TURBO = 14;

    static {
        // native の JNI 関数を解決するために、CMake で生成した共有ライブラリを読み込みます。
        System.loadLibrary("whisper-lib");
    }

    /**
     * モデル読み込みから文字起こし、context 解放までをまとめて行う簡易関数です。
     *
     * <p>{@code modelPath} には APK の assets パスではなく、内部ストレージなどにコピーした
     * 実ファイルの絶対パスを渡してください。</p>
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param pcmData 16kHz・モノラル・float PCM
     * @return 文字起こし結果。失敗時は空文字またはエラーメッセージ
     */
    @NonNull
    public static String transcribe(final String modelPath, final float[] pcmData) {
        long context = initFromFile(modelPath, defaultContextParams());
        if (context == 0) {
            return "model load failed";
        }

        try {
            FullParams params = defaultFullParams(SAMPLING_GREEDY);
            params.printProgress = false;
            params.printSpecial = false;
            params.printRealtime = false;
            params.printTimestamps = false;

            int result = full(context, params, pcmData);
            return result == 0 ? getText(context) : "";
        } finally {
            freeContext(context);
        }
    }

    /**
     * Whisper.cpp のバージョン文字列を返します。
     *
     * @return native ライブラリに組み込まれている Whisper.cpp のバージョン
     */
    public static native String version();

    /**
     * Whisper.cpp / ggml が認識している CPU 機能などのシステム情報を返します。
     *
     * @return システム情報の文字列
     */
    public static native String systemInfo();

    /**
     * context 初期化用パラメータのデフォルト値を返します。
     *
     * @return native 側の既定値で埋めた {@link ContextParams}
     */
    public static native ContextParams defaultContextParams();

    /**
     * full 推論用パラメータのデフォルト値を返します。
     *
     * @param strategy {@link #SAMPLING_GREEDY} または {@link #SAMPLING_BEAM_SEARCH}
     * @return native 側の既定値で埋めた {@link FullParams}
     */
    public static native FullParams defaultFullParams(int strategy);

    /**
     * VAD 実行用パラメータのデフォルト値を返します。
     *
     * @return native 側の既定値で埋めた {@link VadParams}
     */
    public static native VadParams defaultVadParams();

    /**
     * VAD context 初期化用パラメータのデフォルト値を返します。
     *
     * @return native 側の既定値で埋めた {@link VadContextParams}
     */
    public static native VadContextParams defaultVadContextParams();

    /**
     * モデルファイルから Whisper context を初期化します。
     *
     * <p>assets 内のファイル名を直接渡すことはできません。内部ストレージなどへコピーした
     * 実ファイルパスを渡してください。戻り値は {@link #freeContext(long)} で解放します。</p>
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param params context 初期化パラメータ
     * @return native context ハンドル。失敗時は 0
     */
    public static native long initFromFile(String modelPath, ContextParams params);

    /**
     * モデルファイルから、初期 state を持たない Whisper context を初期化します。
     *
     * @param modelPath Whisper モデルファイルの実ファイルパス
     * @param params context 初期化パラメータ
     * @return native context ハンドル。失敗時は 0
     */
    public static native long initFromFileNoState(String modelPath, ContextParams params);

    /**
     * メモリ上のモデルデータから Whisper context を初期化します。
     *
     * <p>assets から読み込んだ byte 配列を直接渡したい場合に使えます。戻り値は
     * {@link #freeContext(long)} で解放します。</p>
     *
     * @param modelBuffer Whisper モデルファイル全体の byte 配列
     * @param params context 初期化パラメータ
     * @return native context ハンドル。失敗時は 0
     */
    public static native long initFromBuffer(byte[] modelBuffer, ContextParams params);

    /**
     * メモリ上のモデルデータから、初期 state を持たない Whisper context を初期化します。
     *
     * @param modelBuffer Whisper モデルファイル全体の byte 配列
     * @param params context 初期化パラメータ
     * @return native context ハンドル。失敗時は 0
     */
    public static native long initFromBufferNoState(byte[] modelBuffer, ContextParams params);

    /**
     * 既存 context から独立した推論 state を作成します。
     *
     * <p>複数の推論状態を分けたい場合に使います。戻り値は {@link #freeState(long)} で解放します。</p>
     *
     * @param context native context ハンドル
     * @return native state ハンドル。失敗時は 0
     */
    public static native long initState(long context);

    /**
     * Whisper context を解放します。
     *
     * @param context {@link #initFromFile(String, ContextParams)} などで取得した context ハンドル
     */
    public static native void freeContext(long context);

    /**
     * Whisper state を解放します。
     *
     * @param state {@link #initState(long)} で取得した state ハンドル
     */
    public static native void freeState(long state);

    /**
     * context に紐づく標準 state で音声全体を文字起こしします。
     *
     * @param context native context ハンドル
     * @param params full 推論パラメータ
     * @param pcmData 16kHz・モノラル・float PCM
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int full(long context, FullParams params, float[] pcmData);

    /**
     * 指定した state を使って音声全体を文字起こしします。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param params full 推論パラメータ
     * @param pcmData 16kHz・モノラル・float PCM
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int fullWithState(long context, long state, FullParams params, float[] pcmData);

    /**
     * 音声を分割し、複数 processor で並列に文字起こしします。
     *
     * @param context native context ハンドル
     * @param params full 推論パラメータ
     * @param pcmData 16kHz・モノラル・float PCM
     * @param processors 並列処理数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int fullParallel(long context, FullParams params, float[] pcmData, int processors);

    /**
     * PCM 音声から mel spectrogram を作成します。
     *
     * @param context native context ハンドル
     * @param samples 16kHz・モノラル・float PCM
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int pcmToMel(long context, float[] samples, int threads);

    /**
     * 指定 state 上で PCM 音声から mel spectrogram を作成します。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param samples 16kHz・モノラル・float PCM
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int pcmToMelWithState(long context, long state, float[] samples, int threads);

    /**
     * 事前計算済みの mel spectrogram を context に設定します。
     *
     * @param context native context ハンドル
     * @param melData mel spectrogram データ
     * @param melLength mel の時間方向の長さ
     * @param melBands mel band 数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int setMel(long context, float[] melData, int melLength, int melBands);

    /**
     * 事前計算済みの mel spectrogram を指定 state に設定します。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param melData mel spectrogram データ
     * @param melLength mel の時間方向の長さ
     * @param melBands mel band 数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int setMelWithState(long context, long state, float[] melData, int melLength, int melBands);

    /**
     * mel spectrogram を encoder に通します。
     *
     * @param context native context ハンドル
     * @param offset mel の開始位置
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int encode(long context, int offset, int threads);

    /**
     * 指定 state 上で mel spectrogram を encoder に通します。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param offset mel の開始位置
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int encodeWithState(long context, long state, int offset, int threads);

    /**
     * token 列を decoder に通します。
     *
     * @param context native context ハンドル
     * @param tokens decoder に入力する token ID 配列
     * @param pastTokens 過去 token 数
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int decode(long context, int[] tokens, int pastTokens, int threads);

    /**
     * 指定 state 上で token 列を decoder に通します。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param tokens decoder に入力する token ID 配列
     * @param pastTokens 過去 token 数
     * @param threads 使用スレッド数
     * @return Whisper.cpp の戻り値。0 なら成功
     */
    public static native int decodeWithState(long context, long state, int[] tokens, int pastTokens, int threads);

    /**
     * テキストを Whisper の token ID 配列に変換します。
     *
     * @param context native context ハンドル
     * @param text token 化する文字列
     * @param maxTokens 返却する token の最大数
     * @return token ID 配列
     */
    public static native int[] tokenize(long context, String text, int maxTokens);

    /**
     * テキストを token 化した場合の token 数を返します。
     *
     * @param context native context ハンドル
     * @param text token 数を調べる文字列
     * @return token 数
     */
    public static native int tokenCount(long context, String text);

    /**
     * token ID を表示用文字列に変換します。
     *
     * @param context native context ハンドル
     * @param token token ID
     * @return token の文字列表現
     */
    public static native String tokenToString(long context, int token);

    /** @return end-of-transcript token ID */
    public static native int tokenEot(long context);

    /** @return start-of-transcript token ID */
    public static native int tokenSot(long context);

    /** @return start-of-lm token ID */
    public static native int tokenSolm(long context);

    /** @return previous-context token ID */
    public static native int tokenPrev(long context);

    /** @return no-speech token ID */
    public static native int tokenNosp(long context);

    /** @return not token ID */
    public static native int tokenNot(long context);

    /** @return begin token ID */
    public static native int tokenBeg(long context);

    /**
     * 指定した言語 ID に対応する language token ID を返します。
     *
     * @param context native context ハンドル
     * @param languageId Whisper の言語 ID
     * @return language token ID
     */
    public static native int tokenLang(long context, int languageId);

    /** @return translate task token ID */
    public static native int tokenTranslate(long context);

    /** @return transcribe task token ID */
    public static native int tokenTranscribe(long context);

    /**
     * Whisper.cpp が持つ最大言語 ID を返します。
     *
     * @return 最大言語 ID
     */
    public static native int langMaxId();

    /**
     * 言語コードから Whisper の言語 ID を取得します。
     *
     * @param language 例: {@code "ja"}、{@code "en"}
     * @return 言語 ID。未対応なら Whisper.cpp 側のエラー値
     */
    public static native int langId(String language);

    /**
     * 言語 ID から短い言語コードを取得します。
     *
     * @param languageId Whisper の言語 ID
     * @return 例: {@code "ja"}、{@code "en"}
     */
    public static native String langStr(int languageId);

    /**
     * 言語 ID から表示用の言語名を取得します。
     *
     * @param languageId Whisper の言語 ID
     * @return 例: {@code "japanese"}、{@code "english"}
     */
    public static native String langStrFull(int languageId);

    /**
     * context の音声特徴量から言語を自動判定します。
     *
     * @param context native context ハンドル
     * @param offsetMs 判定開始位置。ミリ秒
     * @param threads 使用スレッド数
     * @return 判定された言語 ID と各言語の確率
     */
    public static native LangDetection langAutoDetect(long context, int offsetMs, int threads);

    /**
     * 指定 state の音声特徴量から言語を自動判定します。
     *
     * @param context native context ハンドル
     * @param state native state ハンドル
     * @param offsetMs 判定開始位置。ミリ秒
     * @param threads 使用スレッド数
     * @return 判定された言語 ID と各言語の確率
     */
    public static native LangDetection langAutoDetectWithState(long context, long state, int offsetMs, int threads);

    /** @return 現在 context が保持している mel の長さ */
    public static native int nLen(long context);

    /** @return 現在 state が保持している mel の長さ */
    public static native int nLenFromState(long state);

    /** @return vocabulary size */
    public static native int nVocab(long context);

    /** @return text context size */
    public static native int nTextCtx(long context);

    /** @return audio context size */
    public static native int nAudioCtx(long context);

    /** @return 多言語モデルなら true */
    public static native boolean isMultilingual(long context);

    /** @return モデルの vocabulary size */
    public static native int modelNVocab(long context);

    /** @return モデルの audio context size */
    public static native int modelNAudioCtx(long context);

    /** @return モデルの audio state size */
    public static native int modelNAudioState(long context);

    /** @return モデルの audio attention head 数 */
    public static native int modelNAudioHead(long context);

    /** @return モデルの audio layer 数 */
    public static native int modelNAudioLayer(long context);

    /** @return モデルの text context size */
    public static native int modelNTextCtx(long context);

    /** @return モデルの text state size */
    public static native int modelNTextState(long context);

    /** @return モデルの text attention head 数 */
    public static native int modelNTextHead(long context);

    /** @return モデルの text layer 数 */
    public static native int modelNTextLayer(long context);

    /** @return モデルの mel band 数 */
    public static native int modelNMels(long context);

    /** @return モデルの file type */
    public static native int modelFType(long context);

    /** @return モデルの種類 ID */
    public static native int modelType(long context);

    /** @return モデル種類の表示名 */
    public static native String modelTypeReadable(long context);

    /**
     * 最後の decode 結果の logits を返します。
     *
     * @param context native context ハンドル
     * @return vocabulary size 分の logits
     */
    public static native float[] logits(long context);

    /**
     * 指定 state の最後の decode 結果の logits を返します。
     *
     * @param state native state ハンドル
     * @param vocabularySize 返却する vocabulary size
     * @return vocabulary size 分の logits
     */
    public static native float[] logitsFromState(long state, int vocabularySize);

    /** @return full 推論で生成されたセグメント数 */
    public static native int fullNSegments(long context);

    /** @return 指定 state の full 推論で生成されたセグメント数 */
    public static native int fullNSegmentsFromState(long state);

    /** @return full 推論で判定された言語 ID */
    public static native int fullLangId(long context);

    /** @return 指定 state の full 推論で判定された言語 ID */
    public static native int fullLangIdFromState(long state);

    /** @return セグメント開始時刻。10ms 単位 */
    public static native long fullSegmentT0(long context, int segment);

    /** @return 指定 state のセグメント開始時刻。10ms 単位 */
    public static native long fullSegmentT0FromState(long state, int segment);

    /** @return セグメント終了時刻。10ms 単位 */
    public static native long fullSegmentT1(long context, int segment);

    /** @return 指定 state のセグメント終了時刻。10ms 単位 */
    public static native long fullSegmentT1FromState(long state, int segment);

    /** @return 次セグメントで話者が変わる可能性がある場合 true */
    public static native boolean fullSegmentSpeakerTurnNext(long context, int segment);

    /** @return 指定 state で次セグメントに話者変化がある可能性がある場合 true */
    public static native boolean fullSegmentSpeakerTurnNextFromState(long state, int segment);

    /** @return セグメントの文字列 */
    public static native String fullSegmentText(long context, int segment);

    /** @return 指定 state のセグメント文字列 */
    public static native String fullSegmentTextFromState(long state, int segment);

    /** @return セグメント内 token 数 */
    public static native int fullNTokens(long context, int segment);

    /** @return 指定 state のセグメント内 token 数 */
    public static native int fullNTokensFromState(long state, int segment);

    /** @return セグメント内 token の文字列表現 */
    public static native String fullTokenText(long context, int segment, int token);

    /**
     * 指定 state の token 文字列を返します。
     *
     * <p>Whisper.cpp の API 上、token 文字列化には context と state の両方が必要です。</p>
     *
     * @return セグメント内 token の文字列表現
     */
    public static native String fullTokenTextFromState(long context, long state, int segment, int token);

    /** @return セグメント内 token ID */
    public static native int fullTokenId(long context, int segment, int token);

    /** @return 指定 state のセグメント内 token ID */
    public static native int fullTokenIdFromState(long state, int segment, int token);

    /** @return token の詳細情報 */
    public static native TokenData fullTokenData(long context, int segment, int token);

    /** @return 指定 state の token 詳細情報 */
    public static native TokenData fullTokenDataFromState(long state, int segment, int token);

    /** @return token の確率 */
    public static native float fullTokenProbability(long context, int segment, int token);

    /** @return 指定 state の token 確率 */
    public static native float fullTokenProbabilityFromState(long state, int segment, int token);

    /** @return セグメントの no-speech 確率 */
    public static native float fullSegmentNoSpeechProbability(long context, int segment);

    /** @return 指定 state のセグメント no-speech 確率 */
    public static native float fullSegmentNoSpeechProbabilityFromState(long state, int segment);

    /** @return Whisper.cpp が記録した処理時間情報 */
    public static native Timings timings(long context);

    /** 処理時間情報を native ログへ出力します。 */
    public static native void printTimings(long context);

    /** context に記録された処理時間情報をリセットします。 */
    public static native void resetTimings(long context);

    /**
     * VAD モデルファイルから VAD context を初期化します。
     *
     * @param modelPath VAD モデルファイルの実ファイルパス
     * @param params VAD context 初期化パラメータ
     * @return native VAD context ハンドル。失敗時は 0
     */
    public static native long vadInitFromFile(String modelPath, VadContextParams params);

    /**
     * 音声に発話が含まれるか判定します。
     *
     * @param vadContext native VAD context ハンドル
     * @param samples 16kHz・モノラル・float PCM
     * @return 発話ありなら true
     */
    public static native boolean vadDetectSpeech(long vadContext, float[] samples);

    /**
     * VAD の内部状態をリセットせずに、音声に発話が含まれるか判定します。
     *
     * @param vadContext native VAD context ハンドル
     * @param samples 16kHz・モノラル・float PCM
     * @return 発話ありなら true
     */
    public static native boolean vadDetectSpeechNoReset(long vadContext, float[] samples);

    /** VAD context の内部状態をリセットします。 */
    public static native void vadResetState(long vadContext);

    /** @return 直近の VAD 推論で得られた発話確率配列 */
    public static native float[] vadProbabilities(long vadContext);

    /**
     * 直近の VAD 確率配列から発話セグメントを作成します。
     *
     * @param vadContext native VAD context ハンドル
     * @param params VAD セグメント化パラメータ
     * @return native VAD segments ハンドル。使用後は {@link #vadFreeSegments(long)}
     */
    public static native long vadSegmentsFromProbabilities(long vadContext, VadParams params);

    /**
     * 音声サンプルから VAD 発話セグメントを作成します。
     *
     * @param vadContext native VAD context ハンドル
     * @param params VAD セグメント化パラメータ
     * @param samples 16kHz・モノラル・float PCM
     * @return native VAD segments ハンドル。使用後は {@link #vadFreeSegments(long)}
     */
    public static native long vadSegmentsFromSamples(long vadContext, VadParams params, float[] samples);

    /** @return VAD セグメント数 */
    public static native int vadSegmentCount(long vadSegments);

    /** @return VAD セグメント開始時刻。秒単位 */
    public static native float vadSegmentT0(long vadSegments, int segment);

    /** @return VAD セグメント終了時刻。秒単位 */
    public static native float vadSegmentT1(long vadSegments, int segment);

    /** VAD セグメントハンドルを解放します。 */
    public static native void vadFreeSegments(long vadSegments);

    /** VAD context ハンドルを解放します。 */
    public static native void vadFree(long vadContext);

    /**
     * Whisper.cpp の memcpy benchmark を実行します。
     *
     * @param threads 使用スレッド数
     * @return benchmark の数値結果
     */
    public static native int benchMemcpy(int threads);

    /**
     * Whisper.cpp の memcpy benchmark 結果を文字列で返します。
     *
     * @param threads 使用スレッド数
     * @return benchmark 結果文字列
     */
    public static native String benchMemcpyString(int threads);

    /**
     * Whisper.cpp の ggml matrix multiplication benchmark を実行します。
     *
     * @param threads 使用スレッド数
     * @return benchmark の数値結果
     */
    public static native int benchGgmlMulMat(int threads);

    /**
     * Whisper.cpp の ggml matrix multiplication benchmark 結果を文字列で返します。
     *
     * @param threads 使用スレッド数
     * @return benchmark 結果文字列
     */
    public static native String benchGgmlMulMatString(int threads);

    /**
     * full 推論結果を {@link Segment} 配列としてまとめて取得します。
     *
     * @param context native context ハンドル
     * @return セグメント配列
     */
    @NonNull
    public static Segment[] getSegments(long context) {
        final int count = fullNSegments(context);
        final Segment[] segments = new Segment[count];

        for (int i = 0; i < count; i++) {
            segments[i] = collectSegment(context, i);
        }

        return segments;
    }

    /**
     * 指定 state の full 推論結果を {@link Segment} 配列としてまとめて取得します。
     *
     * <p>この簡易関数では token の文字列は埋めません。state の token 文字列が必要な場合は
     * {@link #fullTokenTextFromState(long, long, int, int)} を context と一緒に呼んでください。</p>
     *
     * @param state native state ハンドル
     * @return セグメント配列
     */
    @NonNull
    public static Segment[] getSegmentsFromState(long state) {
        final int count = fullNSegmentsFromState(state);
        final Segment[] segments = new Segment[count];

        for (int i = 0; i < count; i++) {
            segments[i] = collectSegmentFromState(state, i);
        }

        return segments;
    }

    /**
     * full 推論結果の全セグメント文字列を連結して返します。
     *
     * @param context native context ハンドル
     * @return 文字起こし結果全体
     */
    @NonNull
    public static String getText(long context) {
        final int count = fullNSegments(context);
        try(PooledStringBuilder sb = ScopableUtility.getBuilder()) {

            for (int i = 0; i < count; i++) {
                sb.append(fullSegmentText(context, i));
            }
            return sb.toString();
        }
    }

    /**
     * context とモデルに関する情報をまとめて取得します。
     *
     * @param context native context ハンドル
     * @return モデル情報
     */
    @NonNull
    public static ModelInfo getModelInfo(long context) {
        final ModelInfo info = new ModelInfo();
        info.nLen = nLen(context);
        info.nVocab = nVocab(context);
        info.nTextCtx = nTextCtx(context);
        info.nAudioCtx = nAudioCtx(context);
        info.multilingual = isMultilingual(context);
        info.modelNVocab = modelNVocab(context);
        info.modelNAudioCtx = modelNAudioCtx(context);
        info.modelNAudioState = modelNAudioState(context);
        info.modelNAudioHead = modelNAudioHead(context);
        info.modelNAudioLayer = modelNAudioLayer(context);
        info.modelNTextCtx = modelNTextCtx(context);
        info.modelNTextState = modelNTextState(context);
        info.modelNTextHead = modelNTextHead(context);
        info.modelNTextLayer = modelNTextLayer(context);
        info.modelNMels = modelNMels(context);
        info.modelFType = modelFType(context);
        info.modelType = modelType(context);
        info.modelTypeReadable = modelTypeReadable(context);
        return info;
    }

    /**
     * context の segment API から Java 用 {@link Segment} を組み立てます。
     */
    @NonNull
    private static Segment collectSegment(long context, int segmentIndex) {
        final Segment segment = new Segment();
        segment.index = segmentIndex;
        segment.t0 = fullSegmentT0(context, segmentIndex);
        segment.t1 = fullSegmentT1(context, segmentIndex);
        segment.text = fullSegmentText(context, segmentIndex);
        segment.speakerTurnNext = fullSegmentSpeakerTurnNext(context, segmentIndex);
        segment.noSpeechProbability = fullSegmentNoSpeechProbability(context, segmentIndex);

        final int tokenCount = fullNTokens(context, segmentIndex);
        segment.tokens = new TokenData[tokenCount];

        for (int i = 0; i < tokenCount; i++) {
            segment.tokens[i] = fullTokenData(context, segmentIndex, i);
            segment.tokens[i].text = fullTokenText(context, segmentIndex, i);
        }

        return segment;
    }

    /**
     * state の segment API から Java 用 {@link Segment} を組み立てます。
     */
    @NonNull
    private static Segment collectSegmentFromState(long state, int segmentIndex) {
        final Segment segment = new Segment();
        segment.index = segmentIndex;
        segment.t0 = fullSegmentT0FromState(state, segmentIndex);
        segment.t1 = fullSegmentT1FromState(state, segmentIndex);
        segment.text = fullSegmentTextFromState(state, segmentIndex);
        segment.speakerTurnNext = fullSegmentSpeakerTurnNextFromState(state, segmentIndex);
        segment.noSpeechProbability = fullSegmentNoSpeechProbabilityFromState(state, segmentIndex);

        final int tokenCount = fullNTokensFromState(state, segmentIndex);
        segment.tokens = new TokenData[tokenCount];

        for (int i = 0; i < tokenCount; i++) {
            segment.tokens[i] = fullTokenDataFromState(state, segmentIndex, i);
        }

        return segment;
    }

    /** Whisper context 初期化時に使う設定です。 */
    public static class ContextParams {
        /** GPU 利用を試みる場合 true。Android ではビルド設定や端末対応状況に依存します。 */
        public boolean useGpu;

        /** flash attention を有効化する場合 true。対応ビルド/環境でのみ効果があります。 */
        public boolean flashAttn;

        /** 使用する GPU device 番号。 */
        public int gpuDevice;

        /** DTW による token timestamp を有効化する場合 true。 */
        public boolean dtwTokenTimestamps;

        /** DTW token timestamp で使う aheads preset。{@code AHEADS_*} 定数を指定します。 */
        public int dtwAheadsPreset;

        /** {@link #AHEADS_N_TOP_MOST} 使用時の上位 head 数。 */
        public int dtwNTop;

        /** DTW 用に確保するメモリサイズ。 */
        public long dtwMemSize;
    }

    /** Whisper の full 推論で使う設定です。 */
    public static class FullParams {
        /** サンプリング方式。{@link #SAMPLING_GREEDY} または {@link #SAMPLING_BEAM_SEARCH}。 */
        public int strategy = SAMPLING_GREEDY;

        /** 推論に使うスレッド数。 */
        public int nThreads;

        /** 最大 text context 数。 */
        public int nMaxTextCtx;

        /** 音声の開始オフセット。ミリ秒。 */
        public int offsetMs;

        /** 処理する音声長。ミリ秒。0 の場合は全体。 */
        public int durationMs;

        /** true の場合、翻訳タスクとして実行します。false の場合は文字起こしです。 */
        public boolean translate;

        /** true の場合、前回までの context を使いません。 */
        public boolean noContext;

        /** true の場合、タイムスタンプ token を抑制します。 */
        public boolean noTimestamps;

        /** true の場合、結果を単一セグメントにまとめます。 */
        public boolean singleSegment;

        /** 特殊 token も出力する場合 true。 */
        public boolean printSpecial;

        /** 進捗を native ログへ出力する場合 true。 */
        public boolean printProgress;

        /** リアルタイム出力を native ログへ出す場合 true。 */
        public boolean printRealtime;

        /** タイムスタンプを native ログへ出す場合 true。 */
        public boolean printTimestamps;

        /** token 単位のタイムスタンプを計算する場合 true。 */
        public boolean tokenTimestamps;

        /** token timestamp の probability threshold。 */
        public float tholdPt;

        /** token timestamp の probability sum threshold。 */
        public float tholdPtsum;

        /** 1 セグメントの最大文字数。 */
        public int maxLen;

        /** true の場合、単語境界で分割しやすくします。 */
        public boolean splitOnWord;

        /** 1 セグメントの最大 token 数。 */
        public int maxTokens;

        /** debug 出力を有効化する場合 true。 */
        public boolean debugMode;

        /** 使用する audio context 数。0 の場合はモデル既定値です。 */
        public int audioCtx;

        /** tinydiarize を有効化する場合 true。 */
        public boolean tdrzEnable;

        /** 抑制したい出力に対する正規表現。 */
        public String suppressRegex;

        /** 推論開始時に与える初期プロンプト。 */
        public String initialPrompt;

        /** true の場合、initialPrompt を次の推論にも引き継ぎます。 */
        public boolean carryInitialPrompt;

        /** initialPrompt の代わりに直接渡す prompt token 配列。 */
        public int[] promptTokens;

        /** 入力音声の言語コード。例: {@code "ja"}、{@code "en"}。 */
        public String language;

        /** true の場合、言語を自動判定します。 */
        public boolean detectLanguage;

        /** blank token を抑制する場合 true。 */
        public boolean suppressBlank;

        /** non-speech token を抑制する場合 true。 */
        public boolean suppressNst;

        /** サンプリング温度。 */
        public float temperature;

        /** 最初のタイムスタンプの最大値。 */
        public float maxInitialTs;

        /** beam search などで使う length penalty。 */
        public float lengthPenalty;

        /** 温度を上げながら再試行する場合の増分。 */
        public float temperatureInc;

        /** entropy がこの値を超えた場合に失敗扱いにする threshold。 */
        public float entropyThold;

        /** log probability がこの値を下回った場合に失敗扱いにする threshold。 */
        public float logprobThold;

        /** no-speech 判定の threshold。 */
        public float noSpeechThold;

        /** greedy sampling で保持する候補数。 */
        public int greedyBestOf;

        /** beam search の beam size。 */
        public int beamSize;

        /** beam search の patience。 */
        public float beamPatience;

        /** full 推論前に VAD を使う場合 true。 */
        public boolean vad;

        /** VAD モデルファイルの実ファイルパス。 */
        public String vadModelPath;

        /** VAD の詳細パラメータ。 */
        public VadParams vadParams;
    }

    /** VAD で発話区間を検出するための設定です。 */
    public static class VadParams {
        /** 発話ありと判定する確率 threshold。 */
        public float threshold;

        /** 発話として扱う最小時間。ミリ秒。 */
        public int minSpeechDurationMs;

        /** 無音として扱う最小時間。ミリ秒。 */
        public int minSilenceDurationMs;

        /** 1 発話区間として扱う最大時間。秒。 */
        public float maxSpeechDurationS;

        /** 発話区間の前後に追加する余白。ミリ秒。 */
        public int speechPadMs;

        /** VAD セグメント間で重ねるサンプル割合。 */
        public float samplesOverlap;
    }

    /** VAD context 初期化時に使う設定です。 */
    public static class VadContextParams {
        /** VAD 推論に使うスレッド数。 */
        public int nThreads;

        /** GPU 利用を試みる場合 true。 */
        public boolean useGpu;

        /** 使用する GPU device 番号。 */
        public int gpuDevice;
    }

    /** Whisper が返す token 単位の詳細情報です。 */
    public static class TokenData {
        /** token ID。 */
        public int id;

        /** token の内部 ID。 */
        public int tid;

        /** token の確率。 */
        public float p;

        /** token の log probability。 */
        public float plog;

        /** timestamp probability。 */
        public float pt;

        /** timestamp probability の累積値。 */
        public float ptsum;

        /** token 開始時刻。10ms 単位。 */
        public long t0;

        /** token 終了時刻。10ms 単位。 */
        public long t1;

        /** DTW 由来の時刻。 */
        public long tDtw;

        /** voice length 推定値。 */
        public float vlen;

        /** token の文字列表現。 */
        public String text;
    }

    /** full 推論結果の 1 セグメントを表します。 */
    public static class Segment {
        /** セグメント番号。 */
        public int index;

        /** セグメント開始時刻。10ms 単位。 */
        public long t0;

        /** セグメント終了時刻。10ms 単位。 */
        public long t1;

        /** セグメントの文字起こし結果。 */
        public String text;

        /** 次セグメントで話者が変わる可能性がある場合 true。 */
        public boolean speakerTurnNext;

        /** セグメントが無音である確率。 */
        public float noSpeechProbability;

        /** セグメント内の token 詳細情報。 */
        public TokenData[] tokens;
    }

    /** Whisper.cpp が計測した処理時間情報です。 */
    public static class Timings {
        /** サンプリング処理時間。ミリ秒。 */
        public float sampleMs;

        /** encoder 処理時間。ミリ秒。 */
        public float encodeMs;

        /** decoder 処理時間。ミリ秒。 */
        public float decodeMs;

        /** batched decoder 処理時間。ミリ秒。 */
        public float batchdMs;

        /** prompt 処理時間。ミリ秒。 */
        public float promptMs;
    }

    /** 言語自動判定の結果です。 */
    public static class LangDetection {
        /** 最も可能性が高い言語 ID。 */
        public int languageId;

        /** 各言語 ID に対応する確率配列。 */
        public float[] probabilities;
    }

    /** Whisper context とモデル構造の概要情報です。 */
    public static class ModelInfo {
        /** 現在 context が保持している mel の長さ。 */
        public int nLen;

        /** vocabulary size。 */
        public int nVocab;

        /** text context size。 */
        public int nTextCtx;

        /** audio context size。 */
        public int nAudioCtx;

        /** 多言語モデルなら true。 */
        public boolean multilingual;

        /** モデルの vocabulary size。 */
        public int modelNVocab;

        /** モデルの audio context size。 */
        public int modelNAudioCtx;

        /** モデルの audio state size。 */
        public int modelNAudioState;

        /** モデルの audio attention head 数。 */
        public int modelNAudioHead;

        /** モデルの audio layer 数。 */
        public int modelNAudioLayer;

        /** モデルの text context size。 */
        public int modelNTextCtx;

        /** モデルの text state size。 */
        public int modelNTextState;

        /** モデルの text attention head 数。 */
        public int modelNTextHead;

        /** モデルの text layer 数。 */
        public int modelNTextLayer;

        /** モデルの mel band 数。 */
        public int modelNMels;

        /** モデルファイルの type。 */
        public int modelFType;

        /** モデル種類 ID。 */
        public int modelType;

        /** モデル種類の表示名。 */
        public String modelTypeReadable;
    }
}
