package jp.ac.gifu_u.programmingjissen2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import java.io.IOException;

import Utils.MyUtils;
import events.AwaitEvent.AwaiterHub;
import events.Request.PermissionAwaiter;
import events.Threading.ThreadStopAwaiter;
import events.Threading.ThreadStoppedEvent;
import events.Whisper.WhisperTranscriptionAwaiter;
import events.Whisper.WhisperTranscriptionEvent;

public class RecordActivity {
    /** ログ出力用タグです。 */
    private final static String TAG = RecordActivity.class.getSimpleName();

    /** assets に配置している Whisper モデルファイル名です。 */
    private final static String ModelName = "ggml-base.bin";

    /** 内部ストレージへコピーした Whisper モデルファイルの実パスです。 */
    private final String ModelPath;

    /** 権限要求と UI 更新に使う Activity です。 */
    private final Activity activity;

    /** 録音開始/停止を切り替えるボタンです。 */
    private final Button recordButton;

    /** 文字起こし結果を表示する TextView です。 */
    private final TextView resultTextView;

    /** AudioRecord と Whisper.cpp に渡す PCM のサンプリングレートです。 */
    static final int FREQUENCY = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;

    /** 録音権限要求で使う request code です。 */
    static final int REQUESTCODE = 2000;

    /** 録音中かどうかを表します。volatile でマルチスレッド間の参照を安定させます。 */
    private volatile boolean isRecording;

    /** 停止処理中かどうかを表します。停止 Awaiter の二重登録を防ぐために使います。 */
    private volatile boolean isStopping;

    /** AudioRecord の録音スレッドを管理する worker です。 */
    private AudioRecordWorker recordWorker;

    /** Whisper.cpp 推論スレッドを管理する worker です。 */
    private WhisperTranscriptionWorker transcriptionWorker;

    /** Whisper 推論結果イベントを 1 件待つ Awaiter です。 */
    private WhisperTranscriptionAwaiter transcriptionAwaiter;

    /** 録音スレッド停止イベントを待つ Awaiter です。 */
    private ThreadStopAwaiter recordStopAwaiter;

    /** Whisper スレッド停止イベントを待つ Awaiter です。 */
    private ThreadStopAwaiter whisperStopAwaiter;

    /** 録音開始ごとに作る session ID です。 */
    private String transcriptionSessionId;

    /** 録音ごとの文字起こし JSON ファイルを管理する writer です。 */
    private TranscriptionJsonWriter transcriptionJsonWriter;

    /** 録音スレッドが停止済みなら true です。 */
    private volatile boolean recordThreadStopped = true;

    /** Whisper スレッドが停止済みなら true です。 */
    private volatile boolean whisperThreadStopped = true;

    /**
     * 録音 UI と Whisper リアルタイム文字起こしの制御クラスを作成します。
     *
     * @param activity 権限要求と UI 更新に使う Activity
     * @param button 録音開始/停止ボタン
     * @param resultTextView 文字起こし表示先
     */
    public RecordActivity(Activity activity, Button button, TextView resultTextView) {
        String modelPath;
        this.activity = activity;
        try {
            modelPath = MyUtils.prepareModelPath(activity, ModelName);
        } catch (IOException e) {
            Log.e(TAG, "Whisper model prepare failed", e);
            modelPath = "";
        }

        this.ModelPath = modelPath;
        this.recordButton = button;
        this.resultTextView = resultTextView;

        recordButton.setText("録音");
        recordButton.setOnClickListener((view) -> {
            if (!isRecording && !isStopping) {
                if (StartRecord()) {
                    recordButton.setText("停止");
                }
            } else {
                if (StopRecord()) {
                    recordButton.setText("停止中");
                }
            }
        });
    }

    /**
     * 録音権限を確認し、録音とリアルタイム文字起こしを開始します。
     *
     * @return 録音開始できた場合 true。権限待ちの場合は false
     */
    public boolean StartRecord() {
        if (isStopping) {
            outputMessage("停止処理中です");
            return false;
        }

        int bufferSize = AudioRecordWorker.createBufferSize(FREQUENCY);
        if (bufferSize <= 0) {
            outputMessage("録音バッファを作成できませんでした");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionAwaiter awaiter = AwaiterHub.rentAwaiter(PermissionAwaiter.class);
            awaiter.initialize(
                    REQUESTCODE,
                    (result) -> {
                        Log.d(TAG, "Record Permission: " + result);
                        if (result && StartRecordInternal(bufferSize)) {
                            activity.runOnUiThread(() -> recordButton.setText("停止"));
                        } else if (!result) {
                            outputMessage("録音権限が許可されていません");
                        }
                    }
            );

            awaiter.start();

            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUESTCODE);
            return false;
        }

        return StartRecordInternal(bufferSize);
    }

    /**
     * 録音権限取得後に、録音 worker、Whisper worker、JSON writer を作成して開始します。
     *
     * @param bufferSize AudioRecord に渡すバッファサイズ
     * @return worker を開始できた場合 true
     */
    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    private boolean StartRecordInternal(int bufferSize) {
        if (isRecording || isStopping) {
            return false;
        }

        if (ModelPath.isEmpty()) {
            outputMessage("Whisper モデルを準備できませんでした");
            return false;
        }

        transcriptionSessionId = createSessionId();
        transcriptionJsonWriter = new TranscriptionJsonWriter(activity, transcriptionSessionId);
        recordThreadStopped = false;
        whisperThreadStopped = false;

        transcriptionWorker = new WhisperTranscriptionWorker(ModelPath, transcriptionSessionId);
        recordWorker = new AudioRecordWorker(
                FREQUENCY,
                bufferSize,
                transcriptionSessionId + ":record",
                this::submitAudioToWhisper
        );

        waitNextTranscriptionEvent(transcriptionSessionId);
        transcriptionWorker.start();

        if (!recordWorker.start()) {
            isStopping = true;
            recordThreadStopped = true;
            cancelTranscriptionAwaiter();
            requestWhisperThreadStop();
            outputMessage("AudioRecord の初期化に失敗しました");
            completeStopIfNeeded();
            return false;
        }

        isRecording = true;
        outputMessage("録音中...");
        return true;
    }

    /**
     * 録音が開始されている場合、録音と Whisper 推論スレッドを止めます。
     *
     * @return 停止要求を出した場合 true
     */
    public boolean StopRecord() {
        if (isStopping) {
            return true;
        }

        if (!isRecording) {
            return false;
        }

        isRecording = false;
        isStopping = true;

        requestRecordThreadStop();
        requestWhisperThreadStop();
        completeStopIfNeeded();
        return true;
    }

    /**
     * 録音スレッドへ停止を要求し、停止イベントを待つ Awaiter を開始します。
     */
    private void requestRecordThreadStop() {
        // フィールドはnullにし、
        // ローカル変数に移すことで、GCを呼ばれるようにする。
        AudioRecordWorker worker = recordWorker;
        if (worker == null || !worker.isAlive()) {
            recordThreadStopped = true;
            recordWorker = null;
            return;
        }

        waitThreadStopEvent(
                worker.getStopEventId(),
                this::onRecordThreadStopped,
                true
        );

        if (!worker.requestStop()) {
            cancelRecordStopAwaiter();
            recordThreadStopped = true;
            recordWorker = null;
        }
    }

    /**
     * Whisper 推論スレッドへ停止を要求し、停止イベントを待つ Awaiter を開始します。
     */
    private void requestWhisperThreadStop() {
        WhisperTranscriptionWorker worker = transcriptionWorker;
        if (worker == null || !worker.isAlive()) {
            whisperThreadStopped = true;
            transcriptionWorker = null;
            return;
        }

        waitThreadStopEvent(
                worker.getStopEventId(),
                this::onWhisperThreadStopped,
                false
        );

        if (!worker.requestStop()) {
            cancelWhisperStopAwaiter();
            whisperThreadStopped = true;
        }
    }

    /**
     * 指定 threadId の停止イベントを待つ Awaiter を開始します。
     *
     * @param threadId 停止イベントの識別 ID
     * @param callback 停止イベント受信時の処理
     * @param recordThreadEvent 録音スレッド用 Awaiter として保持する場合 true
     */
    private void waitThreadStopEvent(
            String threadId,
            java.util.function.Consumer<ThreadStoppedEvent> callback,
            boolean recordThreadEvent
    ) {
        ThreadStopAwaiter awaiter = AwaiterHub.rentAwaiter(ThreadStopAwaiter.class);
        awaiter.initialize(threadId, callback);

        if (recordThreadEvent) {
            recordStopAwaiter = awaiter;
        } else {
            whisperStopAwaiter = awaiter;
        }

        awaiter.start();
    }

    /**
     * 録音スレッド停止イベントを受け取ったときに呼ばれます。
     *
     * @param event スレッド停止イベント
     */
    private void onRecordThreadStopped(ThreadStoppedEvent event) {
        Log.d(TAG, "Record thread stopped: " + event);
        recordStopAwaiter = null;
        recordThreadStopped = true;
        recordWorker = null;
        completeStopIfNeeded();
    }

    /**
     * Whisper スレッド停止イベントを受け取ったときに呼ばれます。
     *
     * @param event スレッド停止イベント
     */
    private void onWhisperThreadStopped(ThreadStoppedEvent event) {
        Log.d(TAG, "Whisper thread stopped: " + event);
        whisperStopAwaiter = null;
        whisperThreadStopped = true;
        transcriptionWorker = null;
        cancelTranscriptionAwaiter();
        completeStopIfNeeded();
    }

    /**
     * 録音スレッドと Whisper スレッドの両方が止まったら停止処理を完了します。
     */
    private void completeStopIfNeeded() {
        if (!isStopping || !recordThreadStopped || !whisperThreadStopped) {
            return;
        }

        finishTranscriptionJson();
        isStopping = false;
        activity.runOnUiThread(() -> recordButton.setText("録音"));
    }

    /// ==== Event Call backs =======

    /**
     * Whisper 推論結果を AwaiterHub 経由で 1 件待ちます。
     *
     * @param sessionId 待ちたい録音 session ID
     */
    private void waitNextTranscriptionEvent(String sessionId) {
        WhisperTranscriptionAwaiter awaiter =
                AwaiterHub.rentAwaiter(WhisperTranscriptionAwaiter.class);
        transcriptionAwaiter = awaiter;
        awaiter.initialize(sessionId, this::onTranscriptionEvent);
        awaiter.start();
    }

    /**
     * Whisper 推論結果イベントを受け取ったときに呼ばれます。
     *
     * @param event Whisper 推論結果イベント
     */
    private void onTranscriptionEvent(WhisperTranscriptionEvent event) {
        appendTranscriptionJson(event);
        activity.runOnUiThread(() -> outputTranscription(event));

        if (isRecording
                && transcriptionWorker != null
                && !event.finalResult()
                && !event.hasError()) {
            waitNextTranscriptionEvent(event.sessionId());
        }
    }

    /**
     * 録音 worker から受け取った PCM を Whisper worker へ渡します。
     *
     * @param samples 16kHz・モノラル・float PCM
     * @param length samples のうち有効な要素数
     */
    private void submitAudioToWhisper(float[] samples, int length) {
        WhisperTranscriptionWorker worker = transcriptionWorker;
        if (worker != null) {
            worker.submit(samples, length);
        }
    }

    /**
     * Whisper のイベント結果を画面に出力します。
     *
     * @param event Whisper 推論結果イベント
     */
    public void outputTranscription(WhisperTranscriptionEvent event) {
        if (event.hasError()) {
            resultTextView.setText("Whisper エラー: " + event.errorMessage());
            return;
        }

        String label = event.finalResult() ? "最終結果" : "認識中";
        String text = event.text().isEmpty() ? "..." : event.text();
        resultTextView.setText(
                label
                        + " "
                        + event.startMs()
                        + "ms-"
                        + (event.startMs() + event.durationMs())
                        + "ms\n"
                        + text
                        + "\n話者変化: "
                        + event.speakerChanged()
        );
    }

    /**
     * Whisper 推論イベントを録音ごとの JSON ファイルへ追記します。
     *
     * @param event Whisper 推論結果イベント
     */
    private void appendTranscriptionJson(WhisperTranscriptionEvent event) {
        TranscriptionJsonWriter writer = transcriptionJsonWriter;
        if (writer != null) {
            writer.append(event);
        }
    }

    /**
     * 録音終了時に JSON ファイルの終了時刻を保存します。
     */
    private void finishTranscriptionJson() {
        TranscriptionJsonWriter writer = transcriptionJsonWriter;
        transcriptionJsonWriter = null;

        if (writer != null) {
            writer.finish();
            Log.d(TAG, "Transcription JSON saved: " + writer.getOutputFile().getAbsolutePath());
        }
    }

    /**
     * Whisper 推論結果 Awaiter をキャンセルします。
     */
    private void cancelTranscriptionAwaiter() {
        WhisperTranscriptionAwaiter awaiter = transcriptionAwaiter;
        transcriptionAwaiter = null;

        if (awaiter != null && !awaiter.isCompleted() && !awaiter.isCancelled()) {
            awaiter.cancel();
        }
    }

    /**
     * 録音スレッド停止 Awaiter をキャンセルします。
     */
    private void cancelRecordStopAwaiter() {
        ThreadStopAwaiter awaiter = recordStopAwaiter;
        recordStopAwaiter = null;

        if (awaiter != null && !awaiter.isCompleted() && !awaiter.isCancelled()) {
            awaiter.cancel();
        }
    }

    /**
     * Whisper スレッド停止 Awaiter をキャンセルします。
     */
    private void cancelWhisperStopAwaiter() {
        ThreadStopAwaiter awaiter = whisperStopAwaiter;
        whisperStopAwaiter = null;

        if (awaiter != null && !awaiter.isCompleted() && !awaiter.isCancelled()) {
            awaiter.cancel();
        }
    }

    /**
     * 録音 session ID を作成します。
     *
     * @return ファイル名にも使える session ID
     */
    private String createSessionId() {
        return Long.toHexString(System.currentTimeMillis())
                + "-"
                + Long.toHexString(System.nanoTime());
    }

    /**
     * UIスレッドにテキストの更新を命じます
     * @param message 新しいメッセージ
     */
    private void outputMessage(String message) {
        activity.runOnUiThread(() -> resultTextView.setText(message));
    }
}
