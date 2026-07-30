package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import jp.ac.gifu_u.programmingjissen2.Record.Buffer.DirectPcm16Buffer;
import jp.ac.gifu_u.programmingjissen2.Record.Buffer.DirectPcm16Builder;
import jp.ac.gifu_u.programmingjissen2.Record.DecodedAudio;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperTranscriptionWorker;

/** デコード済みmono PCMを一定時間ごとのWhisper PCMへ変換するpackage-private helperです。 */
final class WhisperPcmChunkEmitter {
    private final int sourceSampleRate;
    private final int targetSampleRate;
    private final int sourceSamplesPerChunk;
    private final AudioFilePcmDecoder.DecodedAudioConsumer consumer;
    private final DirectPcm16Builder sourceSamples = new DirectPcm16Builder();

    private PendingChunk pending;
    private DirectPcm16Buffer spareOutput;
    private long emittedSampleCount;
    private int chunkCount;

    /**
     * 一定時間ごとの変換器を作成します。
     * @param sourceSampleRate デコード出力Hz。例: {@code 48000}
     * @param chunkDurationMs 推論窓ms。例: {@code 30000}
     * @param consumer 完成チャンクの同期処理先。例: {@code this::transcribeChunk}
     * @throws IllegalArgumentException レート・窓が0以下、またはチャンク数がint範囲外の場合
     */
    WhisperPcmChunkEmitter(
            final int sourceSampleRate,
            final int chunkDurationMs,
            @NonNull final AudioFilePcmDecoder.DecodedAudioConsumer consumer
    ) {
        if (sourceSampleRate <= 0 || chunkDurationMs <= 0) {
            throw new IllegalArgumentException("sample rate and chunk duration must be positive");
        }
        final long sourceChunkSamples =
                sourceSampleRate * (long) chunkDurationMs / 1000L;
        if (sourceChunkSamples > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("PCM chunk is too large");
        }
        this.sourceSampleRate = sourceSampleRate;
        this.targetSampleRate = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;
        this.sourceSamplesPerChunk = (int) Math.max(1L, sourceChunkSamples);
        this.consumer = consumer;
    }

    /**
     * MediaCodec出力をmono PCM16へ変換し、窓へ追加します。
     * 一定量になったらconsumerを呼び出します。
     * @param codec 出力元codec。例: {@code decoder}
     * @param outputIndex 出力buffer番号。例: {@code 0}
     * @param info 有効byte範囲。例: {@code bufferInfo}
     * @param channelCount チャンネル数。例: {@code 2}
     * @param pcmEncoding PCM形式。例: {@code AudioFormat.ENCODING_PCM_16BIT}
     * @throws IOException 完成済みチャンクのconsumerが失敗した場合
     * @throws IllegalArgumentException PCM形式が未対応の場合
     */
    void appendCodecOutput(
            @NonNull final MediaCodec codec,
            final int outputIndex,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding
    ) throws IOException {
        final java.nio.ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
        if (outputBuffer != null && info.size > 0) {
            appendBuffer(outputBuffer, info, channelCount, pcmEncoding);
        }
    }

    /**
     * raw PCMをmono PCM16へ変換し、窓へ追加します。
     * 一定量になったらconsumerを呼び出します。
     * @param buffer PCM byte列。例: {@code directBuffer}
     * @param info 有効byte範囲。例: {@code bufferInfo}
     * @param channelCount チャンネル数。例: {@code 1}
     * @param pcmEncoding PCM形式。例: {@code AudioFormat.ENCODING_PCM_16BIT}
     * @throws IOException 完成済みチャンクのconsumerが失敗した場合
     */
    void appendRawOutput(
            @NonNull final java.nio.ByteBuffer buffer,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding
    ) throws IOException {
        appendBuffer(buffer, info, channelCount, pcmEncoding);
    }

    /**
     * 末尾の短い窓を出力し、最後のチャンクを通知します。
     * @return 全体の変換結果。例: {@code new DecodeSummary(16000, 16000, 1)}
     * @throws IOException consumerが失敗した場合
     */
    @NonNull
    AudioFilePcmDecoder.DecodeSummary finish() throws IOException {
        if (sourceSamples.size() > 0) {
            preparePendingChunk();
        }
        if (pending != null) {
            consumer.onChunk(pending.audio, pending.startSample, true);
            pending = null;
        }
        return new AudioFilePcmDecoder.DecodeSummary(
                emittedSampleCount, targetSampleRate, chunkCount);
    }

    /**
     * PCM出力を窓境界で分割しながらmono builderへ追加します。
     * 一定量になったらconsumerを呼び出します。
     * @param buffer PCM byte列。例: {@code codecOutput}
     * @param info 有効範囲。例: {@code bufferInfo}
     * @param channelCount チャンネル数。例: {@code 2}
     * @param pcmEncoding PCM形式。例: {@code AudioFormat.ENCODING_PCM_FLOAT}
     * @throws IOException consumerが失敗した場合
     * @throws IllegalArgumentException PCM形式またはチャンネル数が不正な場合
     */
    private void appendBuffer(
            @NonNull final ByteBuffer buffer,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding
    ) throws IOException {
        if (channelCount <= 0) {
            throw new IllegalArgumentException("channelCount must be positive");
        }
        final int bytesPerFrame = bytesPerSample(pcmEncoding) * channelCount;
        int byteOffset = info.offset;
        final int byteEnd = info.offset + info.size;
        while (byteOffset + bytesPerFrame <= byteEnd) {
            final int availableFrames = (byteEnd - byteOffset) / bytesPerFrame;
            final int chunkSpace = sourceSamplesPerChunk - sourceSamples.size();
            final int frames = Math.min(availableFrames, chunkSpace);
            final MediaCodec.BufferInfo part = new MediaCodec.BufferInfo();
            part.set(byteOffset, frames * bytesPerFrame, info.presentationTimeUs, info.flags);
            Pcm16AudioConverter.appendMonoPcm16(
                    buffer, part, channelCount, pcmEncoding, sourceSamples);
            byteOffset += part.size;
            if (sourceSamples.size() == sourceSamplesPerChunk) {
                preparePendingChunk();
            }
        }
    }

    /**
     * PCM形式の1サンプルbyte数を返します。
     * @param pcmEncoding Android PCM定数。例: {@code AudioFormat.ENCODING_PCM_16BIT}
     * @return 1サンプルbyte数。例: {@code 2}
     * @throws IllegalArgumentException 未対応形式の場合
     */
    private static int bytesPerSample(final int pcmEncoding) {
        if (pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            return Short.BYTES;
        }
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            return Float.BYTES;
        }
        if (pcmEncoding == AudioFormat.ENCODING_PCM_8BIT) {
            return Byte.BYTES;
        }
        throw new IllegalArgumentException("Unsupported PCM encoding: " + pcmEncoding);
    }

    /** 現在の入力をDirect PCM16へ変換し、1チャンク先読み状態にします。 */
    private void preparePendingChunk() throws IOException {
        final int outputLength = Pcm16AudioConverter.resampledLength(
                sourceSamples.size(), sourceSampleRate, targetSampleRate);
        final DirectPcm16Buffer output =
                spareOutput != null && spareOutput.sampleCapacity() >= outputLength
                        ? spareOutput
                        : new DirectPcm16Buffer(Math.max(1, outputLength));
        spareOutput = null;
        sourceSamples.resampleInto(sourceSampleRate, targetSampleRate, output);
        sourceSamples.clear();

        if (pending != null) {
            consumer.onChunk(pending.audio, pending.startSample, false);
            spareOutput = DirectPcm16Buffer.wrap(pending.audio.samples());
        }
        pending = new PendingChunk(
                new DecodedAudio(output.bytes(), output.sampleCount(), targetSampleRate),
                emittedSampleCount
        );
        emittedSampleCount += output.sampleCount();
        chunkCount++;
    }

    /** consumerへ渡すまで保持する1チャンクです。 */
    private record PendingChunk(DecodedAudio audio, long startSample) {
    }
}
