package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import Utils.StringPool.StringBufferBuilderPool;
import Whisper.WhisperBridge;
import Utils.ScopableUtility;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.FileTranscriptionSettings;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextFormatter;

/** ファイルまたは録音全体をWhisper.cppのfull APIへ一度だけ渡すworkerです。 */
public final class WhisperCPPTranscriptionWorker implements AutoCloseable {
    private final FileTranscriptionSettings fileSettings;
    private final String vadModelPath;
    private long context;

    /**
     * Whisper.cppモデルを開きます。
     * @param modelPath ggmlモデル。例: {@code "/data/.../ggml-small_q8_0.bin"}
     * @param vadModelPath Silero VADモデル。例: {@code "/data/.../ggml-silero-v6.2.0.bin"}
     * @param settings 推論設定。例: {@code WhisperSettings.defaultSettings()}
     * @throws IOException モデルを読み込めない場合
     */
    public WhisperCPPTranscriptionWorker(
            @NonNull final String modelPath,
            @NonNull final String vadModelPath,
            @NonNull final WhisperSettings settings
    ) throws IOException {
        this.fileSettings = settings.fileTranscription();
        this.vadModelPath = vadModelPath;
        final WhisperBridge.ContextParams contextParams = WhisperBridge.defaultContextParams();
        contextParams.useGpu = false;
        context = WhisperBridge.initFromFile(modelPath, contextParams);
        if (context == 0) {
            throw new IOException("Whisper.cpp model load failed: " + modelPath);
        }
    }

    /**
     * 音声配列全体を分割せず1回のfull呼び出しで推論します。
     * @param samples Direct PCM16全体。例: {@code ByteBuffer.allocateDirect(32000)}
     * @param firstByteOffset 第1区間byte位置。例: {@code 0}
     * @param firstSampleCount 第1区間数。例: {@code 16000}
     * @param secondByteOffset 第2区間byte位置。例: {@code 0}
     * @param secondSampleCount 第2区間数。例: {@code 0}
     * @param includeTimestamps セグメント時刻を本文へ付けるならtrue。例: {@code true}
     * @param timestampOffsetMs ファイル先頭からのチャンク開始時刻。例: {@code 30000}
     * @return 全セグメントを結合した結果。例: {@code new TranscriptionWorkerResult("[00:00.000] ...", false)}
     * @throws IOException native推論が失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(
            @NonNull final ByteBuffer samples,
            final int firstByteOffset,
            final int firstSampleCount,
            final int secondByteOffset,
            final int secondSampleCount,
            final boolean includeTimestamps,
            final long timestampOffsetMs
    ) throws IOException {
        if (context == 0) {
            throw new IOException("Whisper.cpp model is already closed");
        }
        final WhisperBridge.FullParams params = createFullParams(includeTimestamps);
        if (!samples.isDirect()) {
            throw new IllegalArgumentException("samples must be a DirectByteBuffer");
        }
        validateRange(samples, firstByteOffset, firstSampleCount, "first");
        validateRange(samples, secondByteOffset, secondSampleCount, "second");
        final int result = WhisperBridge.fullPcm16(
                context, params, samples,
                firstByteOffset, firstSampleCount,
                secondByteOffset, secondSampleCount);
        if (result != 0) {
            throw new IOException("Whisper.cpp inference failed: " + result);
        }
        return collectResult(includeTimestamps, timestampOffsetMs);
    }

    /**
     * Direct PCM区間がバッファ内か検証します。
     * @param samples Direct保存領域。例: {@code ByteBuffer.allocateDirect(32000)}
     * @param byteOffset byte位置。例: {@code 0}
     * @param sampleCount サンプル数。例: {@code 16000}
     * @param name エラー表示名。例: {@code "first"}
     * @throws IllegalArgumentException offsetが奇数、負数、または範囲外の場合
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

    /**
     * full推論設定を作ります。
     * @param includeTimestamps 時刻tokenを生成するならtrue。例: {@code true}
     * @return Whisper.cpp設定。例: {@code params.vad == true}
     */
    @NonNull
    private WhisperBridge.FullParams createFullParams(final boolean includeTimestamps) {
        final WhisperBridge.FullParams params =
                WhisperBridge.defaultFullParams(WhisperBridge.SAMPLING_GREEDY);
        params.printProgress = false;
        params.printSpecial = false;
        params.printRealtime = false;
        params.printTimestamps = true;
        params.noTimestamps = !includeTimestamps;
        params.noContext = false;
        params.nThreads = Math.min(
                fileSettings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        params.translate = fileSettings.translateToEnglish();
        params.language = fileSettings.language();
        params.initialPrompt = fileSettings.prompt();
        params.carryInitialPrompt = true;
        if (fileSettings.vadEnabled()) {
            WhisperVadConfig.enable(params, vadModelPath, fileSettings.vadThreshold());
        }
        return params;
    }

    /**
     * nativeセグメントを共通結果へまとめます。
     * @param includeTimestamps 時刻を本文へ付けるならtrue。例: {@code true}
     * @param timestampOffsetMs ファイル先頭からのチャンク開始時刻。例: {@code 30000}
     * @return 結合結果。例: {@code new TranscriptionWorkerResult("本文", false)}
     */
    @NonNull
    private TranscriptionWorkerResult collectResult(
            final boolean includeTimestamps,
            final long timestampOffsetMs
    ) {
        final int segmentCount = WhisperBridge.fullNSegments(context);
        Log.d("CPP",String.valueOf(segmentCount));
        try (StringBufferBuilderPool builder = ScopableUtility.getBuilder()) {
            boolean speakerChanged = false;
            for (int index = 0; index < segmentCount; index++) {
                final String text = WhisperBridge.fullSegmentText(context, index);
                Log.d("",text);
                builder.append(includeTimestamps
                        ? TranscriptionTextFormatter.formatLine(
                                Math.max(0L, timestampOffsetMs)
                                        + WhisperBridge.fullSegmentT0(context, index) * 10L,
                                text)
                        : text);
                speakerChanged |= WhisperBridge.fullSegmentSpeakerTurnNext(context, index);
            }
            return new TranscriptionWorkerResult(builder.toString(), speakerChanged);
        }
    }

    /** Whisper.cpp contextを解放します。複数回呼んでも安全です。 */
    @Override
    public void close() {
        if (context != 0) {
            WhisperBridge.freeContext(context);
            context = 0;
        }
    }
}
