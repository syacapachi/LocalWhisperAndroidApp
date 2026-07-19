package jp.ac.gifu_u.programmingjissen2.Record;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;

import java.util.List;

/** 録音画面の UI 部品への反映とイベント配線だけを担当します。 */
public final class RecordScreenBinder {
    private final WhisperRecordControls controls;
    private boolean updatingModelSelector;

    /** 録音入力の変更を通知するlistenerです。 */
    public interface AudioSourceSelectedListener {
        /**
         * 選択された録音入力を通知します。
         * @param source 録音入力。例: {@code RecordingAudioSource.APP_CAPTURE}
         */
        void onAudioSourceSelected(RecordingAudioSource source);
    }

    /** モデル選択変更を通知する listener です。 */
    public interface ModelSelectedListener {
        /**
         * モデルRadioGroupで選択されたモデルを通知します。
         *
         * @param model 選択されたモデル。例: {@code WhisperModelOption.BASE}
         */
        void onModelSelected(WhisperModelOption model);
    }

    /**
     * 録音画面UI binderを作成します。
     *
     * @param controls 録音画面のUI参照。例: {@code new WhisperRecordControls(record, inference, settings, group, result, status, benchmark)}
     */
    public RecordScreenBinder(@NonNull final WhisperRecordControls controls) {
        this.controls = controls;
    }

    /**
     * 録音ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> startOrStop()}
     */
    public void setRecordClickListener(final View.OnClickListener listener) {
        if (controls.recordButton != null) {
            controls.recordButton.setOnClickListener(listener);
        }
    }

    /**
     * 推論ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> pauseOrResumeInference()}
     */
    public void setInferenceClickListener(final View.OnClickListener listener) {
        if (controls.inferenceButton != null) {
            controls.inferenceButton.setOnClickListener(listener);
        }
    }

    /**
     * 設定ボタンのクリック処理を設定します。
     *
     * @param listener クリック時処理。例: {@code view -> openSettings()}
     */
    public void setSettingsClickListener(final View.OnClickListener listener) {
        if (controls.settingsButton != null) {
            controls.settingsButton.setOnClickListener(listener);
        }
    }

    /**
     * 録音入力とキャプチャ対象アプリのプルダウンを構成します。
     * @param apps キャプチャ対象候補。例: {@code List.of(new CaptureTargetApp("YouTube", "com.google.android.youtube", 10123))}
     * @param listener 入力変更通知先。例: {@code source -> refreshSourceUi(source)}
     */
    public void bindAudioSourceSelectors(
            @NonNull final List<CaptureTargetApp> apps,
            @NonNull final AudioSourceSelectedListener listener
    ) {
        if (controls.recordingSourceSpinner == null) {
            return;
        }
        final ArrayAdapter<RecordingAudioSource> sourceAdapter = new ArrayAdapter<>(
                controls.recordingSourceSpinner.getContext(),
                android.R.layout.simple_spinner_item,
                RecordingAudioSource.values()
        );
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        controls.recordingSourceSpinner.setAdapter(sourceAdapter);
        controls.recordingSourceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            final AdapterView<?> parent,
                            final View view,
                            final int position,
                            final long id
                    ) {
                        final RecordingAudioSource source = RecordingAudioSource.values()[position];
                        setCaptureAppSelectorVisible(source.requiresAppCapture());
                        listener.onAudioSourceSelected(source);
                    }

                    @Override
                    public void onNothingSelected(final AdapterView<?> parent) { }
                }
        );
        if (controls.captureTargetAppSpinner != null) {
            final ArrayAdapter<CaptureTargetApp> appAdapter = new ArrayAdapter<>(
                    controls.captureTargetAppSpinner.getContext(),
                    android.R.layout.simple_spinner_item,
                    apps
            );
            appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            controls.captureTargetAppSpinner.setAdapter(appAdapter);
        }
    }

    /** @return 選択中の入力。例: {@code RecordingAudioSource.MICROPHONE} */
    @NonNull
    public RecordingAudioSource selectedAudioSource() {
        if (controls.recordingSourceSpinner == null
                || controls.recordingSourceSpinner.getSelectedItem() == null) {
            return RecordingAudioSource.MICROPHONE;
        }
        return (RecordingAudioSource) controls.recordingSourceSpinner.getSelectedItem();
    }

    /** @return 選択中の対象アプリ。未選択ならnull。例: {@code new CaptureTargetApp("YouTube", "com.google.android.youtube", 10123)} */
    public CaptureTargetApp selectedCaptureTargetApp() {
        if (controls.captureTargetAppSpinner == null) {
            return null;
        }
        return (CaptureTargetApp) controls.captureTargetAppSpinner.getSelectedItem();
    }

    /**
     * 録音入力選択を操作可能または固定状態へします。
     * @param enabled 操作可能ならtrue。例: {@code false}
     */
    public void setAudioSourceSelectorsEnabled(final boolean enabled) {
        if (controls.recordingSourceSpinner != null) {
            controls.recordingSourceSpinner.setEnabled(enabled);
        }
        if (controls.captureTargetAppSpinner != null) {
            controls.captureTargetAppSpinner.setEnabled(enabled);
        }
    }

    /**
     * キャプチャ対象アプリ選択の表示状態を切り替えます。
     * @param visible 表示する場合true。例: {@code true}
     */
    private void setCaptureAppSelectorVisible(final boolean visible) {
        if (controls.captureTargetAppSpinner != null) {
            controls.captureTargetAppSpinner.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * モデルassetsパスのドロップダウンと変更処理を設定します。
     *
     * @param currentModel 現在のモデル。例: {@code WhisperModelOption.BASE}
     * @param listener 選択変更通知先。例: {@code this::onModelSelected}
     */
    public void bindModelSelector(
            @NonNull final WhisperModelOption currentModel,
            @NonNull final ModelSelectedListener listener
    ) {
        if (controls.modelSpinner == null) {
            return;
        }
        final ArrayAdapter<WhisperModelOption> adapter = new ArrayAdapter<>(
                controls.modelSpinner.getContext(),
                android.R.layout.simple_spinner_item,
                WhisperModelOption.values()
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        controls.modelSpinner.setAdapter(adapter);
        syncModelSelector(currentModel);
        controls.modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    final AdapterView<?> parent,
                    final View view,
                    final int position,
                    final long id
            ) {
                if (!updatingModelSelector) {
                    listener.onModelSelected(WhisperModelOption.values()[position]);
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) { }
        });
    }

    /**
     * モデルパスドロップダウンの選択状態を画面へ反映します。
     *
     * @param model 選択状態にするモデル。例: {@code WhisperModelOption.SMALL}
     */
    public void syncModelSelector(@NonNull final WhisperModelOption model) {
        if (controls.modelSpinner == null) {
            return;
        }

        updatingModelSelector = true;
        controls.modelSpinner.setSelection(model.ordinal(), false);
        updatingModelSelector = false;
    }

    /**
     * 録音ボタンの表示を状態に合わせて更新します。
     *
     * @param state 現在の文字起こし状態。例: {@code RecordTranscriptionState.Recording}
     */
    public void setRecordButtonState(final RecordTranscriptionState state) {
        if (controls.recordButton == null) {
            return;
        }
        controls.recordButton.setText(state == RecordTranscriptionState.Recording
                ? "録音停止" : "録音開始");
    }

    /**
     * 推論ボタンの表示・文言・操作可否を更新します。
     *
     * @param recording 録音中ならtrue。例: {@code true}
     * @param inferenceAlive 推論workerが生存中ならtrue。例: {@code true}
     * @param inferenceAccepting 音声受付中ならtrue。例: {@code false}
     */
    public void setInferenceButtonState(
            final boolean recording,
            final boolean inferenceAlive,
            final boolean inferenceAccepting
    ) {
        if (controls.inferenceButton == null) {
            return;
        }
        controls.inferenceButton.setVisibility(
                recording || inferenceAlive ? View.VISIBLE : View.GONE
        );
        controls.inferenceButton.setText(inferenceAccepting ? "推論停止" : "推論再開");
        controls.inferenceButton.setEnabled(recording);
    }

    /**
     * 結果表示欄へメッセージを表示します。
     *
     * @param message 表示するメッセージ。例: {@code "録音中です"}
     */
    public void showMessage(final String message) {
        if (controls.resultTextView != null) {
            controls.resultTextView.setText(message);
        }
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
        if (controls.statusTextView != null) {
            controls.statusTextView.setText(value);
        }
    }

    /**
     * ベンチマーク表示欄へ文字列を表示します。
     *
     * @param value 表示する統計文字列。例: {@code "base: 推論時間 未計測"}
     */
    public void setBenchmarkText(final String value) {
        if (controls.benchmarkTextView != null) {
            controls.benchmarkTextView.setText(value);
        }
    }

    /** 表示用文字列を組み立てます。@param label 例: {@code "認識中"} @param text 例: {@code "こんにちは"} @param event 例: {@code WhisperTranscriptionEvent} @return 表示文字列。例: {@code "認識中 0ms-1000ms\n..."} */
    @NonNull
    private String buildTranscriptionViewText(
            @NonNull final String label,
            @NonNull final String text,
            @NonNull final WhisperTranscriptionEvent event
    ) {
        final WhisperModelOption model = WhisperModelOption.fromKey(event.modelKey());
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

}
