package CTranslate2;

import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

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
        Log.d("CTranslate2Bridge",
                StringBufferBuilderPool.Join(",",modelDirectory,computeType,threads));
        handle = create(modelDirectory, computeType, vadModelPath, Math.max(1, threads));
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model load failed: " + modelDirectory);
        }
    }

    /**
     * 16kHzモノラルPCMをWhisperで文字起こしします。
     *
     * @param samples Direct PCM16保存領域。例: {@code ByteBuffer.allocateDirect(160000)}
     * @param firstByteOffset 第1区間のbyte offset。例: {@code 96000}
     * @param firstSampleCount 第1区間のサンプル数。例: {@code 32000}
     * @param secondByteOffset 第2区間のbyte offset。例: {@code 0}
     * @param secondSampleCount 第2区間のサンプル数。例: {@code 48000}
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
            @NonNull final ByteBuffer samples,
            final int firstByteOffset,
            final int firstSampleCount,
            final int secondByteOffset,
            final int secondSampleCount,
            @NonNull final String language,
            final boolean translateToEnglish,
            @NonNull final String initialPrompt,
            final boolean vadEnabled,
            final float vadThreshold
    ) {
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model is already closed");
        }
        if (!samples.isDirect()) {
            throw new IllegalArgumentException("samples must be a DirectByteBuffer");
        }
        validateRange(samples, firstByteOffset, firstSampleCount, "first");
        validateRange(samples, secondByteOffset, secondSampleCount, "second");
        return transcribe(
                handle,
                samples,
                firstByteOffset,
                firstSampleCount,
                secondByteOffset,
                secondSampleCount,
                language,
                translateToEnglish,
                initialPrompt,
                vadEnabled,
                Math.max(0.0f, Math.min(1.0f, vadThreshold)),
                1,
                448
        );
    }

    /**
     * JNIへ渡すPCM区間がDirectバッファ内か検証します。
     * @param samples Direct保存領域。例: {@code ByteBuffer.allocateDirect(160000)}
     * @param byteOffset byte位置。例: {@code 32000}
     * @param sampleCount サンプル数。例: {@code 64000}
     * @param name エラー表示名。例: {@code "first"}
     * @throws IllegalArgumentException offsetが奇数、負数、または容量外の場合
     */
    private static void validateRange(
            @NonNull final ByteBuffer samples,
            final int byteOffset,
            final int sampleCount,
            @NonNull final String name
    ) {
        if (byteOffset < 0 || (byteOffset & 1) != 0 || sampleCount < 0
                || (long) byteOffset + (long) sampleCount * Short.BYTES > samples.capacity()) {
            throw new IllegalArgumentException(name + " PCM16 range is outside samples");
        }
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
            ByteBuffer samples,
            int firstByteOffset,
            int firstSampleCount,
            int secondByteOffset,
            int secondSampleCount,
            String language,
            boolean translateToEnglish,
            String initialPrompt,
            boolean vadEnabled,
            float vadThreshold,
            int beamSize,
            int maxLength
    );
}
