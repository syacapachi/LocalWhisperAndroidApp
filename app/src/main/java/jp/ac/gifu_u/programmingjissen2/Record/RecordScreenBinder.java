package jp.ac.gifu_u.programmingjissen2.Record;

import android.view.View;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import Utils.StringPool.StringBufferBuilderPool;
import events.Whisper.WhisperTranscriptionEvent;
import jp.ac.gifu_u.programmingjissen2.R;
import jp.ac.gifu_u.programmingjissen2.SettingUI.Data.WhisperModelOption;
import jp.ac.gifu_u.programmingjissen2.SettingUI.WhisperRecordControls;

/** 録音画面の UI 部品への反映とイベント配線だけを担当します。 */
public final class RecordScreenBinder {
    private final WhisperRecordControls controls;
    private boolean updatingModelSelector;

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
     * @param controls 録音画面のUI参照。例: {@code new WhisperRecordControls(button, settings, group, result, status, benchmark)}
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
     * モデル選択RadioGroupの変更処理を設定します。
     *
     * @param currentModel 現在のモデル。例: {@code WhisperModelOption.BASE}
     * @param listener 選択変更通知先。例: {@code this::onModelSelected}
     */
    public void bindModelSelector(
            @NonNull final WhisperModelOption currentModel,
            @NonNull final ModelSelectedListener listener
    ) {
        syncModelSelector(currentModel);
        if (controls.modelRadioGroup == null) {
            return;
        }

        controls.modelRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (updatingModelSelector) {
                return;
            }
            listener.onModelSelected(modelFromRadioId(checkedId));
        });
    }

    /**
     * モデルRadioGroupの選択状態を画面へ反映します。
     *
     * @param model 選択状態にするモデル。例: {@code WhisperModelOption.SMALL}
     */
    public void syncModelSelector(@NonNull final WhisperModelOption model) {
        if (controls.modelRadioGroup == null) {
            return;
        }

        updatingModelSelector = true;
        controls.modelRadioGroup.check(model == WhisperModelOption.SMALL
                ? R.id.whisperModelSmall
                : R.id.whisperModelBase);
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
                ? "録音停止"
                : (state == RecordTranscriptionState.StopRecord
                ? "推論停止"
                : (state == RecordTranscriptionState.StopAll ? "推論停止中" : "録音")));
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

    /** RadioButton ID をモデルへ変換します。@param checkedId 例: {@code R.id.whisperModelSmall} @return モデル。例: {@code WhisperModelOption.SMALL} */
    private WhisperModelOption modelFromRadioId(final int checkedId) {
        if (checkedId == R.id.whisperModelSmall) {
            return WhisperModelOption.SMALL;
        }
        return WhisperModelOption.BASE;
    }
}
