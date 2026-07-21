package CTranslate2;

import android.util.Log;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;

/** CTranslate2 の Whisper 推論を Java から呼び出す JNI ブリッジです。 */
public final class CTranslate2Bridge implements AutoCloseable {
    private long handle;

    static {
        System.loadLibrary("ctranslate2-jni");
    }

    /**
     * 変換済みWhisperモデルをCPUへ読み込みます。
     *
     * @param modelDirectory モデルディレクトリ。例: {@code "/data/user/0/.../ct2/base"}
     * @param computeType 計算型。例: {@code "int8"}
     * @param vadModelPath Silero VADモデル。無効時はnull。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param threads 推論スレッド数。例: {@code 4}
     * @throws IllegalArgumentException 引数が不正な場合
     * @throws IllegalStateException モデルを読み込めない場合
     */
    public CTranslate2Bridge(
            @NonNull final String modelDirectory,
            @NonNull final String computeType,
            final String vadModelPath,
            final int threads
    ) {
        Log.d("CTranslate2Bridge", StringBufferBuilderPool.Join(",",modelDirectory,computeType,threads));
        handle = create(modelDirectory, computeType, vadModelPath, Math.max(1, threads));
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model load failed: " + modelDirectory);
        }
    }

    /**
     * VADを読み込まずCTranslate2モデルを開く互換コンストラクタです。
     * @param modelDirectory モデルディレクトリ。例: {@code "/data/.../ct2/base"}
     * @param computeType 計算型。例: {@code "int8"}
     * @param threads 推論スレッド数。例: {@code 4}
     * @throws IllegalStateException CTranslate2モデルまたはSilero VADモデルを読み込めない場合
     */
    public CTranslate2Bridge(
            @NonNull final String modelDirectory,
            @NonNull final String computeType,
            final int threads
    ) {
        this(modelDirectory, computeType, null, threads);
    }

    /**
     * 16kHzモノラルPCMをWhisperで文字起こしします。
     *
     * @param samples 16bit PCM。例: {@code new short[80000]}
     * @param language Whisper言語コード。例: {@code "ja"}
     * @param translateToEnglish 英語翻訳ならtrue。例: {@code false}
     * @param initialPrompt 初期プロンプトと直前文脈。例: {@code "専門用語: CTranslate2\n前の文"}
     * @param vadEnabled 推論前にSilero VADを使う場合true。例: {@code true}
     * @param vadThreshold 発話確率の閾値0～1。例: {@code 0.6f}
     * @return VAD発話区間を推論した本文。発話区間がない場合は空文字。例: {@code "こんにちは"}
     * @throws IllegalStateException 解放後、またはnative推論に失敗した場合
     * @throws IllegalArgumentException nativeへ渡す値が不正な場合
     */
    @NonNull
    public synchronized String transcribe(
            @NonNull final short[] samples,
            @NonNull final String language,
            final boolean translateToEnglish,
            @NonNull final String initialPrompt,
            final boolean vadEnabled,
            final float vadThreshold
    ) {
        return transcribe(samples, samples.length, language, translateToEnglish,
                initialPrompt, vadEnabled, vadThreshold);
    }

    /**
     * 再利用PCM16配列の有効部分だけをWhisperで文字起こしします。
     * @param samples 再利用可能なPCM16配列。例: {@code new short[80000]}
     * @param sampleCount 有効サンプル数。例: {@code 64000}
     * @param language Whisper言語コード。例: {@code "ja"}
     * @param translateToEnglish 英語翻訳ならtrue。例: {@code false}
     * @param initialPrompt 初期プロンプト。例: {@code "Whisper CTranslate2"}
     * @param vadEnabled 無音判定を使う場合true。例: {@code true}
     * @param vadThreshold Silero VADの発話確率閾値。例: {@code 0.6f}
     * @return 文字起こし本文。例: {@code "こんにちは"}
     * @throws IllegalArgumentException sampleCountが配列範囲外の場合
     * @throws IllegalStateException 解放後、またはnative推論に失敗した場合
     */
    @NonNull
    public synchronized String transcribe(
            @NonNull final short[] samples,
            final int sampleCount,
            @NonNull final String language,
            final boolean translateToEnglish,
            @NonNull final String initialPrompt,
            final boolean vadEnabled,
            final float vadThreshold
    ) {
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model is already closed");
        }
        if (sampleCount < 0 || sampleCount > samples.length) {
            throw new IllegalArgumentException("sampleCount is outside samples");
        }
        return transcribe(
                handle,
                samples,
                sampleCount,
                language,
                translateToEnglish,
                initialPrompt,
                vadEnabled,
                Math.max(0.0f, Math.min(1.0f, vadThreshold)),
                1,
                448
        );
    }

    /** nativeモデルを解放します。複数回呼んでも安全です。 */
    @Override
    public synchronized void close() {
        if (handle != 0) {
            destroy(handle);
            handle = 0;
        }
    }

    private static native long create(
            String modelDirectory,
            String computeType,
            String vadModelPath,
            int threads
    );
    private static native void destroy(long handle);
    private static native String transcribe(
            long handle,
            short[] samples,
            int sampleCount,
            String language,
            boolean translateToEnglish,
            String initialPrompt,
            boolean vadEnabled,
            float vadThreshold,
            int beamSize,
            int maxLength
    );
}
