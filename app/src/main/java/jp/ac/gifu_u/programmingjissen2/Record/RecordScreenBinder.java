package jp.ac.gifu_u.programmingjissen2.Record;

import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.ITranscriptionModel;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperInferenceEngine;
import jp.ac.gifu_u.programmingjissen2.SettingUI.ExternalModelRepository;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;

/** 録音画面の UI 部品への反映とイベント配線だけを担当します。 */
public final class RecordScreenBinder {
    private final WhisperRecordControls controls;
    private final ITranscriptionModel[] realtimeModels;
    private final ITranscriptionModel[] fileModels;

    /**
     * 録音画面UI binderを作成します。
     *
     * @param controls 確定済み録音画面のUI参照。例: {@code quickStart.controls(settingsButton)}
     */
    public RecordScreenBinder(@NonNull final WhisperRecordControls controls) {
        this.controls = controls;
        final ExternalModelRepository repository =
                new ExternalModelRepository(controls.recordButton.getContext());
        realtimeModels = repository.list(WhisperInferenceEngine.CTRANSLATE2);
        fileModels = repository.list(WhisperInferenceEngine.WHISPER_CPP);
    }

    /**
     * 録音ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> startOrStop()}
     */
    public void setRecordClickListener(final View.OnClickListener listener) {
        controls.recordButton.setOnClickListener(listener);
    }

    /**
     * 推論ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> pauseOrResumeInference()}
     */
    public void setInferenceClickListener(final View.OnClickListener listener) {
        controls.inferenceButton.setOnClickListener(listener);
    }

    /**
     * 録音スレッドの一時停止・再開ボタンへクリック処理を設定します。
     * @param listener クリック時処理。例: {@code view -> pauseOrResumeRecording()}
     */
    public void setRecordingPauseClickListener(final View.OnClickListener listener) {
        controls.recordingPauseButton.setOnClickListener(listener);
    }

    /**
     * 設定ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> openSettings()}
     */
    public void setSettingsClickListener(final View.OnClickListener listener) {
        controls.settingsButton.setOnClickListener(listener);
    }

    /**
     * 確定済みUIの録音入力プルダウンを構成します。
     * 引数と戻り値はなく、固定enum配列のため例外はありません。
     */
    public void bindAudioSourceSelector() {
        final ArrayAdapter<RecordingAudioSource> sourceAdapter = new ArrayAdapter<>(
                controls.recordingSourceSpinner.getContext(),
                android.R.layout.simple_spinner_item,
                RecordingAudioSource.values()
        );
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        controls.recordingSourceSpinner.setAdapter(sourceAdapter);
    }

    /** @return 選択中の入力。例: {@code RecordingAudioSource.MICROPHONE} */
    @NonNull
    public RecordingAudioSource selectedAudioSource() {
        if (controls.recordingSourceSpinner.getSelectedItem() == null) {
            return RecordingAudioSource.MICROPHONE;
        }
        return (RecordingAudioSource) controls.recordingSourceSpinner.getSelectedItem();
    }

    /**
     * 録音入力選択を操作可能または固定状態へします。
     * @param enabled 操作可能ならtrue。例: {@code false}
     */
    public void setAudioSourceSelectorsEnabled(final boolean enabled) {
        controls.recordingSourceSpinner.setEnabled(enabled);
    }

    /**
     * 録音ボタンの表示を状態に合わせて更新します。
     *
     * @param sessionActive セッション継続中ならtrue。例: {@code true}
     */
    public void setRecordButtonState(final boolean sessionActive) {
        controls.recordButton.morphTo(
                sessionActive ? "■" : "●",
                sessionActive ? "録音セッションを終了" : "録音セッションを開始");
    }

    /**
     * 録音スレッドボタンの表示と記号を更新します。
     * @param sessionActive セッション継続中ならtrue。例: {@code true}
     * @param recording 録音スレッドが動作中ならtrue。例: {@code false}
     */
    public void setRecordingPauseButtonState(
            final boolean sessionActive,
            final boolean recording
    ) {
        controls.recordingPauseButton.setVisibility(sessionActive ? View.VISIBLE : View.GONE);
        controls.recordingPauseButton.morphTo(
                recording ? "⏸" : "▸",
                recording ? "録音を一時停止" : "録音を再開");
        controls.recordingPauseButton.setEnabled(sessionActive);
    }

    /**
     * 推論ボタンの表示・文言・操作可否を更新します。
     *
     * @param sessionActive セッション継続中ならtrue。例: {@code true}
     * @param inferenceAccepting 音声受付中ならtrue。例: {@code false}
     */
    public void setInferenceButtonState(
            final boolean sessionActive,
            final boolean inferenceAccepting
    ) {
        controls.inferenceButton.setVisibility(sessionActive ? View.VISIBLE : View.GONE);
        controls.inferenceButton.morphTo(
                inferenceAccepting ? "🅐" : "Ⓐ",
                inferenceAccepting ? "推論を一時停止" : "推論を再開");
        controls.inferenceButton.setEnabled(sessionActive);
    }

    /**
     * 結果表示欄へメッセージを表示します。
     *
     * @param message 表示するメッセージ。例: {@code "録音中です"}
     */
    public void showMessage(final String message) {
        controls.resultTextView.setText(limitLatestText(message, 120));
    }

    /**
     * 最新結果欄へ収まるようUnicodeコードポイント単位で末尾を省略します。
     * @param value 表示候補。例: {@code "長い文字起こし結果"}
     * @param maxCodePoints 上限。例: {@code 120}
     * @return 上限内の文字列。省略時は末尾に三点リーダー。例: {@code "結果…"}
     */
    @NonNull
    private String limitLatestText(final String value, final int maxCodePoints) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        final int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) {
            return value;
        }
        final int start = value.offsetByCodePoints(0, count - maxCodePoints);
        return "…" + value.substring(start);
    }

    /**
     * Whisperイベントを結果表示欄へ表示します。
     *
     * @param event 表示するWhisperイベント。例: {@code WhisperTranscriptionEvent}
     */
    public void showTranscription(@NonNull final WhisperTranscriptionEvent event) {
        if (event.hasError()) {
            showMessage(StringBufferBuilderPool.Join("", "Whisper エラー: ", event.errorMessage()));
            return;
        }

        final String label = event.finalResult() ? "最終結果" : "認識中";
        final String text = event.text().isEmpty() ? "..." : event.text();
        showMessage(buildTranscriptionViewText(label, text, event));
    }

    /**
     * 状態表示欄へ文字列を表示します。
     *
     * @param value 表示する状態文字列。例: {@code "状態: 録音中"}
     */
    public void setStatusText(final String value) {
        controls.statusTextView.setText(value);
    }

    /**
     * ベンチマーク表示欄へ文字列を表示します。
     *
     * @param value 表示する統計文字列。例: {@code "base: 推論時間 未計測"}
     */
    public void setBenchmarkText(final String value) {
        controls.benchmarkTextView.setText(value);
    }

    /** 表示用文字列を組み立てます。@param label 例: {@code "認識中"} @param text 例: {@code "こんにちは"} @param event 例: {@code WhisperTranscriptionEvent} @return 表示文字列。例: {@code "認識中 0ms-1000ms\n..."} */
    @NonNull
    private String buildTranscriptionViewText(
            @NonNull final String label,
            @NonNull final String text,
            @NonNull final WhisperTranscriptionEvent event
    ) {
        return StringBufferBuilderPool.Join(
                "",
                label,
                " ",
                event.startMs(),
                "ms-",
                event.startMs() + event.durationMs(),
                "ms\n",
                "モデル: ",
                modelDisplayName(event.modelKey()),
                " / 推論: ",
                event.processingTimeMs(),
                "ms\n",
                text
        );
    }

    /**
     * CTranslate2またはWhisper.cppの保存キーを表示名へ変換します。
     * @param modelKey モデルキー。例: {@code "cpp-small-q8-0"}
     * @return 対応表示名。不明値はキーそのもの。例: {@code "Whisper.cpp small・Q8_0"}
     */
    @NonNull
    private String modelDisplayName(final String modelKey) {
        for (ITranscriptionModel option : realtimeModels) {
            if (option.key().equals(modelKey)) {
                return option.displayName();
            }
        }
        for (ITranscriptionModel option : fileModels) {
            if (option.key().equals(modelKey)) {
                return option.toString();
            }
        }
        return modelKey == null || modelKey.isEmpty() ? "不明" : modelKey;
    }

}
