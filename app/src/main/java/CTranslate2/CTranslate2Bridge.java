package CTranslate2;

import androidx.annotation.NonNull;

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
     * @param threads 推論スレッド数。例: {@code 4}
     * @throws IllegalArgumentException 引数が不正な場合
     * @throws IllegalStateException モデルを読み込めない場合
     */
    public CTranslate2Bridge(
            @NonNull final String modelDirectory,
            @NonNull final String computeType,
            final int threads
    ) {
        handle = create(modelDirectory, computeType, Math.max(1, threads));
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model load failed: " + modelDirectory);
        }
    }

    /**
     * 16kHzモノラルPCMをWhisperで文字起こしします。
     *
     * @param samples -1～1のfloat PCM。例: {@code new float[80000]}
     * @param language Whisper言語コード。例: {@code "ja"}
     * @return UTF-8へ復号した文字起こし。例: {@code "こんにちは"}
     * @throws IllegalStateException 解放後、またはnative推論に失敗した場合
     */
    @NonNull
    public String transcribe(@NonNull final float[] samples, @NonNull final String language) {
        if (handle == 0) {
            throw new IllegalStateException("CTranslate2 model is already closed");
        }
        return transcribe(handle, samples, language, 1, 448);
    }

    /** nativeモデルを解放します。複数回呼んでも安全です。 */
    @Override
    public synchronized void close() {
        if (handle != 0) {
            destroy(handle);
            handle = 0;
        }
    }

    private static native long create(String modelDirectory, String computeType, int threads);
    private static native void destroy(long handle);
    private static native String transcribe(
            long handle,
            float[] samples,
            String language,
            int beamSize,
            int maxLength
    );
}
