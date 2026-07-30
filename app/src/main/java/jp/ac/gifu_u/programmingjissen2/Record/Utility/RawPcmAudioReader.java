package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.io.InterruptedIOException;

/** MediaExtractor から raw PCM 音声を直接読む helper です。 */
public final class RawPcmAudioReader {
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    private RawPcmAudioReader() {
    }

    /**
     * 選択済み audio/raw track を Whisper 用 PCM へ変換します。
     *
     * @param extractor audio track が select 済みの extractor。例: {@code extractor}
     * @param format extractor から取得した MediaFormat。例: {@code extractor.getTrackFormat(0)}
     * @param chunkDurationMs 1チャンクの目標時間ms。例: {@code 30000}
     * @param consumer 完成チャンクの同期処理先。例: {@code this::transcribeChunk}
     * @return 変換全体の集計。例: {@code result.chunkCount() == 2}
     * @throws IllegalArgumentException 未対応 PCM 形式や不正なチャンネル数の場合
     * @throws java.io.IOException consumerによる推論・保存に失敗した場合
     */
    @NonNull
    public static AudioFilePcmDecoder.DecodeSummary read(
            @NonNull final MediaExtractor extractor,
            @NonNull final MediaFormat format,
            final int chunkDurationMs,
            @NonNull final AudioFilePcmDecoder.DecodedAudioConsumer consumer
    ) throws java.io.IOException {
        final int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        final int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        final int pcmEncoding = readInt(
                format,
                MediaFormat.KEY_PCM_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT
        );
        final int bufferSize = Math.max(
                readInt(format, MediaFormat.KEY_MAX_INPUT_SIZE, DEFAULT_BUFFER_SIZE),
                DEFAULT_BUFFER_SIZE
        );
        // 空のバッファを作成。
        final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        final WhisperPcmChunkEmitter emitter =
                new WhisperPcmChunkEmitter(sampleRate, chunkDurationMs, consumer);

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("File transcription was interrupted");
            }
            buffer.clear();
            // バッファにデータコピー
            final int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                break;
            }
            info.set(0, sampleSize, extractor.getSampleTime(), MediaCodec.BUFFER_FLAG_KEY_FRAME);
            // 変換バッファに保存し、一定量になったら変換してconsumerを呼び出す。
            emitter.appendRawOutput(buffer, info, channelCount, pcmEncoding);
            //　次のサンプルへ内部インデックスを動かす。
            extractor.advance();
        }

        return emitter.finish();
    }

    private static int readInt(
            @NonNull final MediaFormat format,
            @NonNull final String key,
            final int defaultValue
    ) {
        return format.containsKey(key) ? format.getInteger(key) : defaultValue;
    }
}
