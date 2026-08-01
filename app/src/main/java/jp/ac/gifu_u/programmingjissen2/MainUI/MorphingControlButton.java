package jp.ac.gifu_u.programmingjissen2.MainUI;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;

/** 状態記号と輪郭を縮小・展開アニメーションで切り替える操作ボタンです。 */
public final class MorphingControlButton extends MaterialButton {
    private static final long HALF_DURATION_MS = 90L;
    private static final float COLLAPSED_SCALE_X = 0.12f;
    private static final float COLLAPSED_SCALE_Y = 0.82f;

    private String targetGlyph = "";

    /**
     * モーフィング操作ボタンを作成します。
     * @param context 親画面のContext。例: {@code activity}
     */
    public MorphingControlButton(@NonNull final Context context) {
        super(context);
        setAllCaps(false);
        setInsetTop(0);
        setInsetBottom(0);
    }

    /**
     * 初期記号をアニメーションなしで設定します。
     * @param glyph 表示記号。例: {@code "●"}
     * @param description 読み上げ説明。例: {@code "録音セッションを開始"}
     */
    public void initialize(
            @NonNull final String glyph,
            @NonNull final String description
    ) {
        animate().cancel();
        targetGlyph = glyph;
        setScaleX(1f);
        setScaleY(1f);
        applyGlyph(glyph, description);
    }

    /**
     * ボタンを横に畳み、中央で記号と輪郭を交換してから元の形へ展開します。
     * @param glyph 遷移後の記号。例: {@code "■"}
     * @param description 遷移後の読み上げ説明。例: {@code "録音セッションを終了"}
     */
    public void morphTo(
            @NonNull final String glyph,
            @NonNull final String description
    ) {
        if (glyph.equals(targetGlyph)) {
            setContentDescription(description);
            return;
        }
        targetGlyph = glyph;
        animate().cancel();
        if (!isLaidOut() || getVisibility() != View.VISIBLE) {
            initialize(glyph, description);
            return;
        }
        animate()
                .scaleX(COLLAPSED_SCALE_X)
                .scaleY(COLLAPSED_SCALE_Y)
                .setDuration(HALF_DURATION_MS)
                .withEndAction(() -> {
                    if (!glyph.equals(targetGlyph)) {
                        return;
                    }
                    applyGlyph(glyph, description);
                    animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(HALF_DURATION_MS)
                            .start();
                })
                .start();
    }

    /**
     * 記号と状態に対応する角丸量を反映します。
     * @param glyph 表示記号。例: {@code "●"}
     * @param description 読み上げ説明。例: {@code "録音セッションを開始"}
     */
    private void applyGlyph(
            @NonNull final String glyph,
            @NonNull final String description
    ) {
        setText(glyph);
        setContentDescription(description);
        setCornerRadius(dp("●".equals(glyph) ? 24 : 12));
    }

    /** @param value dp値。例: {@code 24} @return pixel値。例: {@code 72} */
    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
