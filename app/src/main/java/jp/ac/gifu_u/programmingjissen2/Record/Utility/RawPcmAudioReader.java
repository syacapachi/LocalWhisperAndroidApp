package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import jp.ac.gifu_u.programmingjissen2.Record.DecodedAudio;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperTranscriptionWorker;

/** MediaExtractor から raw PCM 音声を直接読む helper です。 */
public final class RawPcmAudioReader {
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;

    private RawPcmAudioReader() {
    }

    /**
     * 選択済み audio/raw track を Whisper 用 PCM へ変換します。
     *
     * @param extractor audio track が select 済みの extractor。例: {@code extractor}
     * @param format extractor から取得した MediaFormat。例: {@code extractor.getTrackFormat(0)}
     * @return Whisper に渡せる PCM。例: {@code new DecodedAudio(new float[16000], 16000)}
     * @throws IllegalArgumentException 未対応 PCM 形式や不正なチャンネル数の場合
     */
    @NonNull
    public static DecodedAudio read(
            @NonNull final MediaExtractor extractor,
            @NonNull final MediaFormat format
    ) {
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
        final Pcm16AudioConverter.FloatArrayBuilder samples =
                new Pcm16AudioConverter.FloatArrayBuilder();

        while (true) {
            buffer.clear();
            // バッファにデータコピー
            final int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                break;
            }
            info.set(0, sampleSize, extractor.getSampleTime(), MediaCodec.BUFFER_FLAG_KEY_FRAME);
            Pcm16AudioConverter.appendMonoFloat(buffer, info, channelCount, pcmEncoding, samples);
            //　次のサンプルへ内部インデックスを動かす。
            extractor.advance();
        }

        final float[] whisperPcm = Pcm16AudioConverter.resample(samples.toArray(), sampleRate,
                WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE);
        return new DecodedAudio(whisperPcm, WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE);
    }

    private static int readInt(
            @NonNull final MediaFormat format,
            @NonNull final String key,
            final int defaultValue
    ) {
        return format.containsKey(key) ? format.getInteger(key) : defaultValue;
    }
}
