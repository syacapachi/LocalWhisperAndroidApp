package jp.ac.gifu_u.programmingjissen2.SettingUI;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Locale;

/** SeekBarと右側の数値入力を同期させる設定UI部品です。 */
public final class SeekEditControl {
    private final double min;
    private final double max;
    private final double step;
    private final SeekBar seekBar;
    private final EditText editText;
    private boolean updating;
    /**
     * 数値範囲入力を親へ追加します。
     * @param context View生成用Context。例: {@code activity}
     * @param root 追加先。例: {@code settingsPage}
     * @param label 行の見出し。例: {@code "推論窓（秒）"}
     * @param min 最小値。例: {@code 1.0}
     * @param max 最大値。例: {@code 30.0}
     * @param step 刻み。例: {@code 1.0}
     * @return SeekBarと数値欄を同期するcontrol。例: {@code windowControl}
     * @throws IllegalArgumentException 範囲または刻みが不正な場合
     */
    @NonNull
    public static SeekEditControl add(
            @NonNull final Context context,
            @NonNull final LinearLayout root,
            @NonNull final String label,
            final double min,
            final double max,
            final double step
    ) {
        if (max < min || step <= 0.0) {
            throw new IllegalArgumentException("invalid seek range");
        }
        final TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setPadding(0, dp(context, 10), 0, 0);
        root.addView(labelView);
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final SeekBar seekBar = new SeekBar(context);
        final int steps = Math.max(0, (int) Math.round((max - min) / step));
        seekBar.setMax(steps);
        row.addView(seekBar, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final EditText editText = new EditText(context);
        editText.setSingleLine(true);
        editText.setSelectAllOnFocus(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER
                | (step < 1.0 ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        row.addView(editText, new LinearLayout.LayoutParams(
                dp(context, 76), LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return new SeekEditControl(min, max, step, seekBar, editText);
    }
    /**
     * View間の同期を開始します。
     * @param min 最小値。例: {@code 0.0}
     * @param max 最大値。例: {@code 1.0}
     * @param step 刻み。例: {@code 0.1}
     * @param seekBar 操作バー。例: {@code seekBar}
     * @param editText 右側入力欄。例: {@code editText}
     * 例外はなく、初期値は最小値になります。
     */
    private SeekEditControl(
            final double min,
            final double max,
            final double step,
            @NonNull final SeekBar seekBar,
            @NonNull final EditText editText
    ) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.seekBar = seekBar;
        this.editText = editText;
        bind();
        setValue(min);
    }
    /**
     * 表示値を範囲と刻みに合わせて設定します。
     * @param value 入力値。例: {@code 0.6}
     * 戻り値と例外はありません。
     */
    public void setValue(final double value) {
        final double normalized = normalize(value);
        updating = true;
        seekBar.setProgress(toProgress(normalized));
        final String text = format(normalized);
        editText.setText(text);
        editText.setSelection(text.length());
        updating = false;
    }
    /**
     * 入力値を取得し、不正な文字列なら指定値を使います。
     * @param fallback 変換失敗時の値。例: {@code 0.5}
     * @return 範囲と刻みに補正した値。例: {@code 0.6}
     * 例外は外へ送出しません。
     */
    public double valueOr(final double fallback) {
        try {
            return normalize(Double.parseDouble(editText.getText().toString().trim()));
        } catch (NumberFormatException error) {
            return normalize(fallback);
        }
    }
    /**
     * 値変更監視に使用する右側の数値入力欄を返します。
     * @return 同期対象EditText。例: {@code control.editText()}
     * 例外はありません。
     */
    @NonNull
    public EditText editText() {
        return editText;
    }
    /** SeekBar・数値入力・フォーカス終了時の同期処理を登録します。 */
    private void bind() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    final SeekBar bar,
                    final int progress,
                    final boolean fromUser
            ) {
                if (fromUser && !updating) {
                    setValue(min + progress * step);
                }
            }

            @Override public void onStartTrackingTouch(final SeekBar bar) { }
            @Override public void onStopTrackingTouch(final SeekBar bar) { }
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    final CharSequence value, final int start, final int count, final int after) { }
            @Override public void onTextChanged(
                    final CharSequence value, final int start, final int before, final int count) { }

            @Override
            public void afterTextChanged(final Editable value) {
                if (updating) {
                    return;
                }
                try {
                    updating = true;
                    seekBar.setProgress(toProgress(Double.parseDouble(value.toString())));
                } catch (NumberFormatException ignored) {
                    // 入力途中は確定値へ戻さず、フォーカス終了時に補正します。
                } finally {
                    updating = false;
                }
            }
        });
        editText.setOnFocusChangeListener((view, focused) -> {
            if (!focused) {
                setValue(valueOr(min));
            }
        });
    }
    /** @param value 例: {@code 0.56} @return 範囲・刻み補正値。例: {@code 0.6} */
    private double normalize(final double value) {
        final double clamped = Math.max(min, Math.min(max, value));
        return min + Math.round((clamped - min) / step) * step;
    }
    /** @param value 数値。例: {@code 10.0} @return SeekBar位置。例: {@code 9} */
    private int toProgress(final double value) {
        return Math.max(0, Math.min(
                seekBar.getMax(),
                (int) Math.round((normalize(value) - min) / step)));
    }
    /** @param value 数値。例: {@code 0.5} @return UI文字列。例: {@code "0.5"} */
    @NonNull
    private String format(final double value) {
        return step < 1.0
                ? String.format(Locale.ROOT, "%.1f", value)
                : String.valueOf((int) Math.round(value));
    }
    /** @param context 画面Context。例: {@code activity} @param value dp。例: {@code 76}
     * @return pixel値。例: {@code 228} */
    private static int dp(@NonNull final Context context, final int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
