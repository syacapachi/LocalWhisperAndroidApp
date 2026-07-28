package jp.ac.gifu_u.programmingjissen2.Transcription;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import CTranslate2.CTranslate2Bridge;
import Whisper.WhisperBridge;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;

/** 外部パスをWhisper.cppとCTranslate2で実際に開き、対応形式を判定する専用APIです。 */
public final class ModelLoadProbe {
    private ModelLoadProbe() { }

    /** 両エンジンの読み込み結果です。 */
    public record Result(
            boolean whisperLoaded,
            boolean cTranslate2Loaded,
            @Nullable WhisperInferenceEngine detectedEngine,
            @NonNull String whisperError,
            @NonNull String cTranslate2Error
    ) {
        /** @return どちらか一方以上で読み込めた場合true。例: {@code true} */
        public boolean loaded() { return whisperLoaded || cTranslate2Loaded; }
    }

    /**
     * 両エンジンを順番にtry-catchで試し、開けたnativeモデルを直ちに解放します。
     * @param modelPath モデルファイルまたはディレクトリ。例: {@code "/sdcard/models/small"}
     * @param computeType CTranslate2計算型。例: {@code "int8"}
     * @return 両方式の成否と推奨方式。例: {@code new Result(false, true, CTRANSLATE2, "", "")}
     * @throws IllegalArgumentException modelPathが空の場合
     */
    @NonNull
    public static Result probe(
            @NonNull final String modelPath,
            @NonNull final String computeType
    ) {
        if (modelPath.trim().isEmpty()) {
            throw new IllegalArgumentException("modelPath must not be empty");
        }
        final ProbeAttempt whisper = tryWhisper(modelPath);
        final ProbeAttempt cTranslate2 = tryCTranslate2(modelPath, computeType);
        final WhisperInferenceEngine detected;
        if (whisper.loaded && !cTranslate2.loaded) {
            detected = WhisperInferenceEngine.WHISPER_CPP;
        } else if (cTranslate2.loaded && !whisper.loaded) {
            detected = WhisperInferenceEngine.CTRANSLATE2;
        } else if (whisper.loaded) {
            detected = new java.io.File(modelPath).isDirectory()
                    ? WhisperInferenceEngine.CTRANSLATE2
                    : WhisperInferenceEngine.WHISPER_CPP;
        } else {
            detected = null;
        }
        return new Result(
                whisper.loaded,
                cTranslate2.loaded,
                detected,
                whisper.error,
                cTranslate2.error
        );
    }

    /**
     * Whisper.cppだけでモデル読み込みを試します。
     * @param modelPath ggmlモデルパス。例: {@code "/sdcard/models/ggml-small.bin"}
     * @return 読み込み成功ならtrue。例: {@code true}。native例外はfalseへ変換
     */
    public static boolean canLoadWithWhisper(@NonNull final String modelPath) {
        return tryWhisper(modelPath).loaded;
    }

    /**
     * CTranslate2だけでモデル読み込みを試します。
     * @param modelPath 変換済みモデルディレクトリ。例: {@code "/sdcard/models/ct2-small"}
     * @param computeType 計算型。例: {@code "int8"}
     * @return 読み込み成功ならtrue。例: {@code true}。native例外はfalseへ変換
     */
    public static boolean canLoadWithCTranslate2(
            @NonNull final String modelPath,
            @NonNull final String computeType
    ) {
        return tryCTranslate2(modelPath, computeType).loaded;
    }

    /**
     * Whisper.cpp contextの生成と解放をtry-catch内で行います。
     * @param path ggmlモデル。例: {@code "/sdcard/model.bin"}
     * @return 内部試行結果。例: {@code new ProbeAttempt(true, "")}
     */
    @NonNull
    private static ProbeAttempt tryWhisper(@NonNull final String path) {
        long context = 0;
        try {
            context = WhisperBridge.initFromFile(path, WhisperBridge.defaultContextParams());
            return context == 0
                    ? new ProbeAttempt(false, "Whisper.cpp model load returned 0")
                    : new ProbeAttempt(true, "");
        } catch (RuntimeException | LinkageError error) {
            return new ProbeAttempt(false, errorMessage(error));
        } finally {
            if (context != 0) {
                WhisperBridge.freeContext(context);
            }
        }
    }

    /**
     * CTranslate2 bridgeの生成と解放をtry-catch内で行います。
     * @param path モデルディレクトリ。例: {@code "/sdcard/ct2-small"}
     * @param computeType 計算型。例: {@code "int8"}
     * @return 内部試行結果。例: {@code new ProbeAttempt(true, "")}
     */
    @NonNull
    private static ProbeAttempt tryCTranslate2(
            @NonNull final String path,
            @NonNull final String computeType
    ) {
        try (CTranslate2Bridge ignored = new CTranslate2Bridge(
                path, normalizeComputeType(computeType), null, 1)) {
            return new ProbeAttempt(true, "");
        } catch (RuntimeException | LinkageError error) {
            return new ProbeAttempt(false, errorMessage(error));
        }
    }

    /**
     * 空の計算型をint8へ補正します。
     * @param value 入力。例: {@code ""}
     * @return 補正値。例: {@code "int8"}
     */
    @NonNull
    private static String normalizeComputeType(@NonNull final String value) {
        return value.trim().isEmpty() ? "int8" : value.trim();
    }

    /**
     * 例外を表示用文字列へ変換します。
     * @param error 原因。例: {@code new IllegalStateException("load failed")}
     * @return 表示文。例: {@code "load failed"}
     */
    @NonNull
    private static String errorMessage(@NonNull final Throwable error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    /** 1エンジン分の内部試行結果です。 */
    private record ProbeAttempt(boolean loaded, @NonNull String error) { }
}
