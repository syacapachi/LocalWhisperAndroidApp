package jp.ac.gifu_u.programmingjissen2.Record.Utility;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

import jp.ac.gifu_u.programmingjissen2.Record.DecodedAudio;

/** URIで指定された音声を一定時間ごとのWhisper用Direct PCM16へデコードするclassです。 */
public final class AudioFilePcmDecoder {
    private static final long CODEC_TIMEOUT_US = 10_000L;

    private AudioFilePcmDecoder() {
    }

    /** 完成したPCMチャンクを同期的に処理します。 */
    @FunctionalInterface
    public interface DecodedAudioConsumer {
        /**
         * 1チャンクを処理します。戻るとPCM領域は解放・再利用可能になります。
         * @param audio 16kHz・mono・Direct PCM16。例: {@code decodedAudio}
         * @param startSample ファイル先頭からの16kHzサンプル位置。例: {@code 480000}
         * @param finalChunk 最後のチャンクならtrue。例: {@code false}
         * @throws IOException 推論や保存に失敗した場合
         */
        void onChunk(
                @NonNull DecodedAudio audio,
                long startSample,
                boolean finalChunk
        ) throws IOException;
    }

    /**
     * ストリームデコード全体の集計です。
     * @param sampleCount 16kHz変換後の総サンプル数。例: {@code 960000}
     * @param sampleRate 出力Hz。例: {@code 16000}
     * @param chunkCount 出力チャンク数。例: {@code 2}
     */
    public record DecodeSummary(long sampleCount, int sampleRate, int chunkCount) {
        /** @return 音声全体のms。例: {@code 60000}。例外はありません。 */
        public long durationMs() {
            return sampleCount * 1000L / sampleRate;
        }
    }

    /**
     * 音声ファイルを読み込み、一定時間ごとの16kHz・mono・Direct PCM16を通知します。
     *
     * @param context ContentResolver を取得する Context。例: {@code activity}
     * @param uri ドキュメントピッカーなどで得た音声 URI。例: {@code content://media/...}
     * @param chunkDurationMs 1チャンクの目標時間ms。例: {@code 30000}
     * @param consumer 各チャンクの同期処理先。例: {@code this::transcribeChunk}
     * @return 変換全体の集計。例: {@code summary.chunkCount() == 2}
     * @throws IOException 音声トラックがない、decoder を作れない、読み込みに失敗した場合
     * @throws IllegalArgumentException PCM 変換時に未対応形式が返された場合
     */
    @NonNull
    public static DecodeSummary decodeToWhisperPcm(
            @NonNull final Context context,
            @NonNull final Uri uri,
            final int chunkDurationMs,
            @NonNull final DecodedAudioConsumer consumer
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
                return RawPcmAudioReader.read(
                        extractor, inputFormat, chunkDurationMs, consumer);
            }

            return decodeTrack(
                    extractor, inputFormat, mime, chunkDurationMs, consumer);
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
     * @param chunkDurationMs 1チャンクの目標時間ms。例: {@code 30000}
     * @param consumer 完成チャンクの同期処理先。例: {@code this::transcribeChunk}
     * @return 変換全体の集計。例: {@code summary.sampleRate() == 16000}
     * @throws IOException デコードに失敗した場合
     */
    @NonNull
    private static DecodeSummary decodeTrack(
            @NonNull final MediaExtractor extractor,
            @NonNull final MediaFormat inputFormat,
            @NonNull final String mime,
            final int chunkDurationMs,
            @NonNull final DecodedAudioConsumer consumer
    ) throws IOException {
        // メディアのデコーダを作成。
        final MediaCodec codec = MediaCodec.createDecoderByType(mime);
        int sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
        boolean codecStarted = false;

        try {
            codec.configure(inputFormat, null, null, 0);
            codec.start();
            codecStarted = true;

            WhisperPcmChunkEmitter emitter = null;
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone) {
                throwIfInterrupted();
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
                    if (emitter == null) {
                        emitter = new WhisperPcmChunkEmitter(
                                sampleRate, chunkDurationMs, consumer);
                    }
                    try {
                        //　変換バッファに追加。一定量になったら変換してconsumerを呼ぶ。
                        emitter.appendCodecOutput(
                                codec, outputIndex, info, channelCount, pcmEncoding);
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                }
            }
            return emitter.finish();
        } finally {
            if (codecStarted) {
                codec.stop();
            }
            codec.release();
        }

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

    /**
     * 呼び出しスレッドの中断をデコード停止へ変換します。
     * 戻り値はありません。
     * @throws InterruptedIOException スレッドへinterrupt済みの場合
     */
    private static void throwIfInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("File transcription was interrupted");
        }
    }

}
