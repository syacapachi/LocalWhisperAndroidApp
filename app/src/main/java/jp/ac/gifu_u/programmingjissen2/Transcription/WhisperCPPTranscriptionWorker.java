package jp.ac.gifu_u.programmingjissen2.Transcription;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;

import Whisper.WhisperBridge;
import Utils.ScopableUtility;
import Utils.StringPool.PooledStringBuilder;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.TranscriptionText.TranscriptionTextFormatter;

/** ファイルまたは録音全体をWhisper.cppのfull APIへ一度だけ渡すworkerです。 */
public final class WhisperCPPTranscriptionWorker implements AutoCloseable {
    private final WhisperSettings settings;
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
        this.settings = settings;
        this.vadModelPath = vadModelPath;
        final WhisperBridge.ContextParams contextParams = WhisperBridge.defaultContextParams();
        contextParams.useGpu = settings.useGpu();
        context = WhisperBridge.initFromFile(modelPath, contextParams);
        if (context == 0) {
            throw new IOException("Whisper.cpp model load failed: " + modelPath);
        }
    }

    /**
     * 音声配列全体を分割せず1回のfull呼び出しで推論します。
     * @param samples 16kHzモノラルfloat PCM全体。例: {@code decodedAudio.samples()}
     * @param includeTimestamps セグメント時刻を本文へ付けるならtrue。例: {@code true}
     * @return 全セグメントを結合した結果。例: {@code new TranscriptionWorkerResult("[00:00.000] ...", false)}
     * @throws IOException native推論が失敗した場合
     */
    @NonNull
    public TranscriptionWorkerResult transcribe(
            @NonNull final float[] samples,
            final boolean includeTimestamps
    ) throws IOException {
        if (context == 0) {
            throw new IOException("Whisper.cpp model is already closed");
        }
        final WhisperBridge.FullParams params = createFullParams(includeTimestamps);
        final int result = WhisperBridge.full(context, params, samples);
        if (result != 0) {
            throw new IOException("Whisper.cpp inference failed: " + result);
        }
        return collectResult(includeTimestamps);
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
                settings.maxThreads(),
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );
        params.translate = settings.translateToEnglish();
        params.language = settings.language();
        params.initialPrompt = settings.prompt();
        params.carryInitialPrompt = true;
        if (settings.vadEnabled()) {
            WhisperVadConfig.enable(params, vadModelPath, settings.sileroVadThreshold());
        }
        return params;
    }

    /**
     * nativeセグメントを共通結果へまとめます。
     * @param includeTimestamps 時刻を本文へ付けるならtrue。例: {@code true}
     * @return 結合結果。例: {@code new TranscriptionWorkerResult("本文", false)}
     */
    @NonNull
    private TranscriptionWorkerResult collectResult(final boolean includeTimestamps) {
        final int segmentCount = WhisperBridge.fullNSegments(context);
        Log.d("CPP",String.valueOf(segmentCount));
        try (PooledStringBuilder builder = ScopableUtility.getBuilder()) {
            boolean speakerChanged = false;
            for (int index = 0; index < segmentCount; index++) {
                final String text = WhisperBridge.fullSegmentText(context, index);
                Log.d("",text);
                builder.append(includeTimestamps
                        ? TranscriptionTextFormatter.formatLine(
                                WhisperBridge.fullSegmentT0(context, index) * 10L, text)
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
