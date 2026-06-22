package jp.ac.gifu_u.programmingjissen2;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

import Utils.StringPool.StringBufferBuilderPool;
import events.AwaitEvent.AwaiterHub;
import events.Request.PermissionAwaiter;
import events.SystemEventHub;
import events.Whisper.WhisperRecordingStateEvent;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperInferenceStats;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperRecordControls;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperSettings;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperSettingsActivity;
import jp.ac.gifu_u.programmingjissen2.UI.WhisperSettingsStore;

/** 録音画面の UI とバックグラウンド Whisper サービスを接続するクラスです。 */
public class RecordActivity {
    /** AudioRecord と Whisper.cpp に渡す PCM のサンプリングレートです。 */
    static final int FREQUENCY = WhisperTranscriptionWorker.DEFAULT_SAMPLE_RATE;

    /** 録音権限要求で使う request code です。 */
    static final int REQUESTCODE = 2000;

    private final Activity activity;
    private final Button recordButton;
    private final Button settingsButton;
    private final RadioGroup modelRadioGroup;
    private final TextView resultTextView;
    private final TextView statusTextView;
    private final TextView benchmarkTextView;
    private final WhisperSettingsStore settingsStore;

    private final Consumer<WhisperTranscriptionEvent> transcriptionListener =
            this::onTranscriptionEvent;
    private final Consumer<WhisperRecordingStateEvent> stateListener = this::onRecordingStateEvent;

    private volatile boolean isRecording;
    private volatile boolean isStopping;
    private WhisperSettings currentSettings;
    private boolean updatingModelSelector;

    /** 従来の最小 UI で録音制御クラスを作成します。 */
    public RecordActivity(Activity activity, Button button, TextView resultTextView) {
        this(activity, new WhisperRecordControls(
                button,
                null,
                null,
                resultTextView,
                null,
                null
        ));
    }

    /** 録音 UI とバックグラウンド Whisper サービスの制御クラスを作成します。 */
    public RecordActivity(Activity activity, WhisperRecordControls controls) {
        this.activity = activity;
        this.recordButton = controls.recordButton;
        this.settingsButton = controls.settingsButton;
        this.modelRadioGroup = controls.modelRadioGroup;
        this.resultTextView = controls.resultTextView;
        this.statusTextView = controls.statusTextView;
        this.benchmarkTextView = controls.benchmarkTextView;
        this.settingsStore = new WhisperSettingsStore(activity);
        this.currentSettings = settingsStore.load();

        SystemEventHub.subscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.subscribe(WhisperRecordingStateEvent.class, stateListener);

        setupRecordButton();
        setupSettingsButton();
        setupModelSelector();
        RefreshSettings();
    }

    private void setupRecordButton() {
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

    private void setupSettingsButton() {
        if (settingsButton == null) {
            return;
        }
        // 設定UI画面を上に重ねる
        settingsButton.setOnClickListener((view) -> activity.startActivity(
                new Intent(activity, WhisperSettingsActivity.class)
        ));
    }

    private void setupModelSelector() {
        syncModelSelector(currentSettings.model());
        if (modelRadioGroup == null) {
            return;
        }

        modelRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingModelSelector) {
                return;
            }

            WhisperModelOption selected = modelFromRadioId(checkedId);
            if (selected == currentSettings.model()) {
                return;
            }

            if (isRecording || isStopping) {
                outputMessage("モデル変更は録音停止後に反映できます");
                syncModelSelector(currentSettings.model());
                return;
            }

            currentSettings = currentSettings.withModel(selected);
            settingsStore.save(currentSettings);
            refreshWhisperInfo();
        });
    }

    /** 設定画面から戻ったときなどに、保存済み設定を録音画面へ反映します。 */
    public void RefreshSettings() {
        if (!isRecording && !isStopping) {
            currentSettings = settingsStore.load();
            syncModelSelector(currentSettings.model());
        }
        isRecording = BackgroundWhisperService.isRunning();
        isStopping = BackgroundWhisperService.isStopping();
        recordButton.setText(isRecording ? "停止" : (isStopping ? "停止中" : "録音"));
        refreshWhisperInfo();

        String latest = BackgroundWhisperService.latestText();
        if (latest != null && !latest.isEmpty()) {
            resultTextView.setText(latest);
        }
    }

    /** 録音権限を確認し、バックグラウンド録音サービスを開始します。 */
    public boolean StartRecord() {
        if (isStopping) {
            outputMessage("停止処理中です");
            return false;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionAwaiter awaiter = AwaiterHub.rentAwaiter(PermissionAwaiter.class);
            awaiter.initialize(
                    REQUESTCODE,
                    (result) -> {
                        if (result) {
                            requestPostNotificationPermissionIfNeeded();
                            activity.runOnUiThread(() -> {
                                if (StartRecord()) {
                                    recordButton.setText("停止");
                                }
                            });
                        } else {
                            outputMessage("録音権限が許可されていません");
                        }
                    }
            );
            awaiter.start();
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUESTCODE
            );
            return false;
        }

        requestPostNotificationPermissionIfNeeded();
        currentSettings = settingsStore.load();
        BackgroundWhisperService.startRecording(activity);
        isRecording = true;
        isStopping = false;
        recordButton.setText("停止");
        outputMessage(StringBufferBuilderPool.Join(
                "",
                "バックグラウンド録音を開始します: ",
                currentSettings.model().displayName()
        ));
        refreshWhisperInfo();
        return true;
    }

    /** バックグラウンド録音サービスへ停止を要求します。 */
    public boolean StopRecord() {
        if (isStopping) {
            return true;
        }

        if (!isRecording && !BackgroundWhisperService.isRunning()) {
            return false;
        }

        isRecording = false;
        isStopping = true;
        BackgroundWhisperService.stopRecording(activity);
        recordButton.setText("停止中");
        refreshWhisperInfo();
        return true;
    }

    /** Activity の破棄時に UI 側の購読だけ解除します。 */
    public void Dispose() {
        SystemEventHub.unsubscribe(WhisperTranscriptionEvent.class, transcriptionListener);
        SystemEventHub.unsubscribe(WhisperRecordingStateEvent.class, stateListener);
    }

    private void onTranscriptionEvent(WhisperTranscriptionEvent event) {
        activity.runOnUiThread(() -> {
            outputTranscription(event);
            refreshWhisperInfo();
        });
    }

    private void onRecordingStateEvent(WhisperRecordingStateEvent event) {
        activity.runOnUiThread(() -> {
            isRecording = event.recording();
            isStopping = event.stopping();
            recordButton.setText(isRecording ? "停止" : (isStopping ? "停止中" : "録音"));
            if (!event.latestText().isEmpty()) {
                resultTextView.setText(event.latestText());
            } else if (!event.message().isEmpty()) {
                resultTextView.setText(event.message());
            }
            refreshWhisperInfo();
        });
    }

    /** Whisper のイベント結果を画面に出力します。 */
    public void outputTranscription(WhisperTranscriptionEvent event) {
        if (event.hasError()) {
            resultTextView.setText(StringBufferBuilderPool.Join(
                    "",
                    "Whisper エラー: ",
                    event.errorMessage()
            ));
            return;
        }

        String label = event.finalResult() ? "最終結果" : "認識中";
        String text = event.text().isEmpty() ? "..." : event.text();
        resultTextView.setText(buildTranscriptionViewText(label, text, event));
    }

    private String buildTranscriptionViewText(
            String label,
            String text,
            WhisperTranscriptionEvent event
    ) {
        WhisperModelOption model = WhisperModelOption.fromKey(event.modelKey());
        return StringBufferBuilderPool.Join(
                "",
                label,
                " ",
                event.startMs(),
                "ms-",
                event.startMs() + event.durationMs(),
                "ms\n",
                "モデル: ",
                model.displayName(),
                " / 推論: ",
                event.processingTimeMs(),
                "ms\n",
                text
        );
    }

    private void refreshWhisperInfo() {
        WhisperSettings settings = currentSettings;
        String state = isRecording ? "録音中" : (isStopping ? "停止中" : "待機中");
        setStatusText(StringBufferBuilderPool.Join(
                "",
                "状態: ",
                state,
                " / モデル: ",
                settings.model().displayName(),
                " / 言語: ",
                settings.language(),
                " / 窓: ",
                settings.windowMs(),
                "ms / 重なり: ",
                settings.overlapMs(),
                "ms"
        ));
        setBenchmarkText(buildBenchmarkText());
    }

    private String buildBenchmarkText() {
        return StringBufferBuilderPool.Join(
                "",
                formatStats(WhisperModelOption.BASE),
                "\n",
                formatStats(WhisperModelOption.SMALL)
        );
    }

    private String formatStats(WhisperModelOption model) {
        WhisperInferenceStats stats = settingsStore.loadStats(model);
        if (!stats.hasSamples()) {
            return StringBufferBuilderPool.Join(
                    "",
                    model.displayName(),
                    ": 推論時間 未計測"
            );
        }

        return StringBufferBuilderPool.Join(
                "",
                model.displayName(),
                ": 最新 ",
                stats.lastMs(),
                "ms / 平均 ",
                stats.averageMs(),
                "ms / ",
                stats.count(),
                "回"
        );
    }

    private void syncModelSelector(WhisperModelOption model) {
        if (modelRadioGroup == null) {
            return;
        }

        updatingModelSelector = true;
        modelRadioGroup.check(model == WhisperModelOption.SMALL
                ? R.id.whisperModelSmall
                : R.id.whisperModelBase);
        updatingModelSelector = false;
    }

    private WhisperModelOption modelFromRadioId(int checkedId) {
        if (checkedId == R.id.whisperModelSmall) {
            return WhisperModelOption.SMALL;
        }
        return WhisperModelOption.BASE;
    }

    private void requestPostNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUESTCODE + 1
        );
    }

    private void outputMessage(String message) {
        activity.runOnUiThread(() -> resultTextView.setText(message));
    }

    private void setStatusText(String value) {
        if (statusTextView != null) {
            statusTextView.setText(value);
        }
    }

    private void setBenchmarkText(String value) {
        if (benchmarkTextView != null) {
            benchmarkTextView.setText(value);
        }
    }
}
