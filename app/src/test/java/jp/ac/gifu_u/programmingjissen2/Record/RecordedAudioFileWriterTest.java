package jp.ac.gifu_u.programmingjissen2.Record;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;

import org.junit.Test;

/** WAV録音writerのヘッダー確定とPCM変換を検証します。 */
public class RecordedAudioFileWriterTest {
    /** 2サンプル保存後のWAVサイズ、data長、PCM値を確認します。 */
    @Test
    public void closeFinalizesWavHeaderAndSamples() throws Exception {
        final File directory = Files.createTempDirectory("recorded-audio-test").toFile();
        final RecordedAudioFileWriter writer =
                new RecordedAudioFileWriter(directory, "record:test", 16000);
        final File outputFile = writer.getOutputFile();

        assertEquals(2, writer.append(new short[]{Short.MIN_VALUE, Short.MAX_VALUE, 0}, 2));
        writer.close();

        assertEquals(48, outputFile.length());
        try (RandomAccessFile input = new RandomAccessFile(outputFile, "r")) {
            input.seek(40);
            assertEquals(4, readLittleEndianInt(input));
            assertEquals(-32768, readLittleEndianShort(input));
            assertEquals(32767, readLittleEndianShort(input));
        }
    }

    /**
     * little endianの32bit整数を読みます。
     * @param input WAV入力。例: {@code new RandomAccessFile(file, "r")}
     * @return 読み取った値。例: {@code 4}
     * @throws Exception 読み込みに失敗した場合
     */
    private int readLittleEndianInt(final RandomAccessFile input) throws Exception {
        return input.readUnsignedByte()
                | input.readUnsignedByte() << 8
                | input.readUnsignedByte() << 16
                | input.readUnsignedByte() << 24;
    }

    /**
     * little endianの16bit符号付き整数を読みます。
     * @param input WAV入力。例: {@code new RandomAccessFile(file, "r")}
     * @return 読み取った値。例: {@code -32768}
     * @throws Exception 読み込みに失敗した場合
     */
    private short readLittleEndianShort(final RandomAccessFile input) throws Exception {
        return (short) (input.readUnsignedByte() | input.readUnsignedByte() << 8);
    }
}
