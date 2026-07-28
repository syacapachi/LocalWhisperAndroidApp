package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import jp.ac.gifu_u.programmingjissen2.Record.DecodedAudio;
import jp.ac.gifu_u.programmingjissen2.Record.Buffer.DirectPcm16Buffer;
import jp.ac.gifu_u.programmingjissen2.Record.Buffer.DirectPcm16Builder;
import jp.ac.gifu_u.programmingjissen2.Transcription.WhisperTranscriptionWorker;

/** URI で指定された音声ファイルを Whisper 用 PCM へデコードする class です。 */
public final class AudioFilePcmDecoder {
    private static final long CODEC_TIMEOUT_US = 10_000L;

    private AudioFilePcmDecoder() {
    }

    /**
     * 音声ファイルを読み込み、16kHz・mono・float PCM に変換します。
     *
     * @param context ContentResolver を取得する Context。例: {@code activity}
     * @param uri ドキュメントピッカーなどで得た音声 URI。例: {@code content://media/...}
     * @return Whisperに渡せるDirect PCM16。例: {@code decodedAudio.sampleCount() == 16000}
     * @throws IOException 音声トラックがない、decoder を作れない、読み込みに失敗した場合
     * @throws IllegalArgumentException PCM 変換時に未対応形式が返された場合
     */
    @NonNull
    public static DecodedAudio decodeToWhisperPcm(
            @NonNull final Context context,
            @NonNull final Uri uri
    ) throws IOException {
        // 音声データをデコードする MediaExtractor を作成
        final MediaExtractor extractor = new MediaExtractor();
        try {
            // uriからメディアを読み込む
            extractor.setDataSource(context, uri, null);
            final int trackIndex = findAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("audio track not found");
            }

            // メディアから音声とらっくを読み込む
            extractor.selectTrack(trackIndex);
            // 音声トラックのフォーマットを取得
            final MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
            // メディア種類の識別子(mime) を取得
            final String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("audio/")) {
                throw new IOException("invalid audio mime: " + mime);
            }
            // RAWタイプなら RawPcmAudioReader でデコード
            if (MediaFormat.MIMETYPE_AUDIO_RAW.equals(mime)) {
                return RawPcmAudioReader.read(extractor, inputFormat);
            }

            return decodeTrack(extractor, inputFormat, mime);
        } finally {
            extractor.release();
        }
    }

    /**
     * 音声トラックを検索します。
     * @param extractor 音声データを読み込む MediaExtractor
     * @return 音声トラックの index。見つからなかった場合 -1
     */
    private static int findAudioTrack(@NonNull final MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            final MediaFormat format = extractor.getTrackFormat(i);
            final String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 音声データをデコードする MediaCodec を作成し、デコードした PCM を返します。
     * @param extractor 音声データを読み込む MediaExtractor
     * @param inputFormat 音声トラックのフォーマット
     * @param mime 音声トラックの mime
     * @return デコードした PCM
     * @throws IOException デコードに失敗した場合
     */
    @NonNull
    private static DecodedAudio decodeTrack(
            @NonNull final MediaExtractor extractor,
            @NonNull final MediaFormat inputFormat,
            @NonNull final String mime
    ) throws IOException {
        // メディアのデコーダを作成。
        final MediaCodec codec = MediaCodec.createDecoderByType(mime);
        // 出力音声バッファを作成。
        final DirectPcm16Builder samples = new DirectPcm16Builder();
        int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        boolean codecStarted = false;

        try {
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            codecStarted = true;

            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                if (!inputDone) {
                    // 少しづつ変換
                    inputDone = queueInputBuffer(extractor, codec);
                }

                final int outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                }
                //　途中でフォーマットが変わった場合更新
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    final MediaFormat outputFormat = codec.getOutputFormat();
                    sampleRate = readInt(outputFormat, MediaFormat.KEY_SAMPLE_RATE, sampleRate);
                    channelCount = readInt(outputFormat, MediaFormat.KEY_CHANNEL_COUNT, channelCount);
                    pcmEncoding = readInt(outputFormat, MediaFormat.KEY_PCM_ENCODING, pcmEncoding);
                    continue;
                }
                // 出力バッファに追加
                if (outputIndex >= 0) {
                    appendOutputBuffer(codec, outputIndex, info, channelCount, pcmEncoding, samples);
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
            }
        } finally {
            if (codecStarted) {
                codec.stop();
            }
            codec.release();
        }

        final int targetSampleRate = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;
        final DirectPcm16Buffer whisperPcm = samples.resample(sampleRate, targetSampleRate);
        return new DecodedAudio(
                whisperPcm.bytes(),
                whisperPcm.sampleCount(),
                WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE);
    }

    /**
     * 入力バッファに値を追加します。
     * @param extractor メディアの読み込みに使用される MediaExtractor
     * @param codec メディアのデコーダ
     * @return 入力バッファが空になった場合 true
     */
    private static boolean queueInputBuffer(
            @NonNull final MediaExtractor extractor,
            @NonNull final MediaCodec codec
    ) {
        // データの数を取得
        final int inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
        if (inputIndex < 0) {
            return false;
        }
        // データバッファを取得
        final ByteBuffer inputBuffer = codec.getInputBuffer(inputIndex);
        if (inputBuffer == null) {
            codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
            );
            return true;
        }

        inputBuffer.clear();
        // バッファ分だけデータを読む
        final int sampleSize = extractor.readSampleData(inputBuffer, 0);
        if (sampleSize < 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return true;
        }

        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(),
                extractor.getSampleFlags());
        // 内部インデックスを更新
        extractor.advance();
        return false;
    }

    /**
     * 出力バッファに値を追加します。
     * @param codec MediaCodec
     * @param outputIndex 出力バッファの index
     * @param info MediaCodec.BufferInfo
     * @param channelCount 出力音声のチャンネル数
     * @param pcmEncoding 出力音声のエンコーディング
     * @param samples 出力バッファ
     */
    private static void appendOutputBuffer(
            @NonNull final MediaCodec codec,
            final int outputIndex,
            @NonNull final MediaCodec.BufferInfo info,
            final int channelCount,
            final int pcmEncoding,
            @NonNull final DirectPcm16Builder samples
    ) {
        final ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
        if (outputBuffer != null && info.size > 0) {
            Pcm16AudioConverter.appendMonoPcm16(
                    outputBuffer,
                    info,
                    channelCount,
                    pcmEncoding,
                    samples
            );
        }
        codec.releaseOutputBuffer(outputIndex, false);
    }

    /**
     *  MediaFormat から int を取得します。
     * @param format 変換元 MediaFormat
     * @param key 取得する値のキー文字列
     * @param defaultValue デフォルト値
     * @return 取得した値。見つからなかった場合 defaultValue
     */
    private static int readInt(
            @NonNull final MediaFormat format,
            @NonNull final String key,
            final int defaultValue
    ) {
        return format.containsKey(key) ? format.getInteger(key) : defaultValue;
    }

}
