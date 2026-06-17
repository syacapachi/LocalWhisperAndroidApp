package Whisper;

public class WhisperBridge {

    static {
        System.loadLibrary("whisper-lib");
    }

    //C++を呼ぶ
    public native String transcribe(
            String modelPath,
            float[] pcmData
    );
}