package jp.ac.gifu_u.programmingjissen2.Record;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** マイクのPCM16を録音単位のWAVファイルへ保存します。 */
public final class RecordedAudioFileWriter implements AutoCloseable {
    public static final String DIRECTORY_NAME = "recordings";
    private static final int HEADER_SIZE = 44;
    private static final int CHANNEL_COUNT = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final File outputFile;
    private final RandomAccessFile output;
    private final int sampleRate;
    private long pcmBytes;
    private boolean closed;

    /**
     * 録音保存先を作成し、空のWAVヘッダーを書き込みます。
     *
     * @param context アプリ内保存先を得るContext。例: {@code service}
     * @param sessionId 録音を識別するID。例: {@code "record-a1b2"}
     * @param sampleRate サンプリングレートHz。例: {@code 16000}
     * @throws IOException ディレクトリまたはファイルを作成できない場合
     */
    public RecordedAudioFileWriter(
            @NonNull final Context context,
            @NonNull final String sessionId,
            final int sampleRate
    ) throws IOException {
        this(new File(context.getFilesDir(), DIRECTORY_NAME), sessionId, sampleRate);
    }

    /**
     * 指定ディレクトリへ録音保存先を作成します。テストでも利用します。
     * @param directory 保存先。例: {@code new File("recordings")}
     * @param sessionId 録音ID。例: {@code "record-a1b2"}
     * @param sampleRate Hz。例: {@code 16000}
     * @throws IOException ディレクトリまたはファイルを作成できない場合
     */
    RecordedAudioFileWriter(
            @NonNull final File directory,
            @NonNull final String sessionId,
            final int sampleRate
    ) throws IOException {
        this.sampleRate = sampleRate;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("recording directory creation failed: " + directory);
        }
        outputFile = new File(directory, sanitize(sessionId) + ".wav");
        output = new RandomAccessFile(outputFile, "rw");
        output.setLength(0);
        writeHeader(0);
    }

    /**
     * PCM16の有効部分をWAVへ追記します。
     *
     * @param samples モノラルPCM16。例: {@code new short[]{0, 16384}}
     * @param length 有効サンプル数。例: {@code 2}
     * @return 実際に保存したサンプル数。例: {@code 2}
     * @throws IOException ファイルが閉じている、または書き込みに失敗した場合
     */
    public synchronized int append(@NonNull final short[] samples, final int length)
            throws IOException {
        if (closed) {
            throw new IOException("recording file is already closed");
        }
        final int count = Math.max(0, Math.min(length, samples.length));
        for (int i = 0; i < count; i++) {
            writeLittleEndianShort(samples[i]);
        }
        pcmBytes += (long) count * Short.BYTES;
        return count;
    }

    /**
     * 保存中のWAVファイルを返します。
     *
     * @return 録音ファイル。例: {@code /data/user/0/.../files/recordings/record-a1b2.wav}
     */
    @NonNull
    public File getOutputFile() {
        return outputFile;
    }

    /**
     * WAVヘッダーへ最終サイズを反映してファイルを閉じます。
     *
     * @throws IOException ヘッダー更新またはcloseに失敗した場合
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        output.seek(0);
        writeHeader(pcmBytes);
        output.close();
        closed = true;
    }

    /** @param dataSize PCMバイト数。例: {@code 32000} @throws IOException 書き込み失敗時 */
    private void writeHeader(final long dataSize) throws IOException {
        output.writeBytes("RIFF");
        writeLittleEndianInt(36L + dataSize);
        output.writeBytes("WAVEfmt ");
        writeLittleEndianInt(16);
        writeLittleEndianShort((short) 1);
        writeLittleEndianShort((short) CHANNEL_COUNT);
        writeLittleEndianInt(sampleRate);
        writeLittleEndianInt((long) sampleRate * CHANNEL_COUNT * BITS_PER_SAMPLE / 8);
        writeLittleEndianShort((short) (CHANNEL_COUNT * BITS_PER_SAMPLE / 8));
        writeLittleEndianShort((short) BITS_PER_SAMPLE);
        output.writeBytes("data");
        writeLittleEndianInt(dataSize);
        if (output.getFilePointer() != HEADER_SIZE) {
            throw new IOException("invalid WAV header size");
        }
    }

    /** @param value 16bit値。例: {@code (short) 1} @throws IOException 書き込み失敗時 */
    private void writeLittleEndianShort(final short value) throws IOException {
        output.write(value & 0xff);
        output.write((value >>> 8) & 0xff);
    }

    /** @param value 32bit値。例: {@code 16000} @throws IOException 書き込み失敗時 */
    private void writeLittleEndianInt(final long value) throws IOException {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
    }

    /** @param value 元ID。例: {@code "record:a"} @return ファイル名用文字列。例: {@code "record_a"} */
    @NonNull
    private static String sanitize(@NonNull final String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
