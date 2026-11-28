package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.utils.glass.source.GlassSourceColor;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentFloatingButton extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
    private final int ANIMATOR_ID_BUTTON_VISIBLE = 0;
    private final int ANIMATOR_ID_PROGRESS_VISIBLE = 1;

    private final BoolAnimator animatorButtonVisible = new BoolAnimator(ANIMATOR_ID_BUTTON_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);

    private final BoolAnimator animatorProgressVisible = new BoolAnimator(ANIMATOR_ID_PROGRESS_VISIBLE, this,
        CubicBezierInterpolator.EASE_OUT_QUINT, 380);


    public final RLottieImageView imageView;
    public final RadialProgressView progressView;
    private final Theme.ResourcesProvider resourcesProvider;
    private ArrayList<View> additionalContentViews;
    private final boolean isSubButton;

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, resourcesProvider, false);
    }

    public FragmentFloatingButton(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean isSubButton) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        this.isSubButton = isSubButton;

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setSize(dp(18));
        progressView.setStrokeWidth(2);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setAnimatedVisibility(progressView, 0);

        ScaleStateListAnimator.apply(this);
        if (!isSubButton) {
            setOutlineProvider(ViewOutlineProviderImpl.BOUNDS_OVAL);
            setTranslationZ(dpf2(0.5f));
        }

        if (isSubButton) {
            iBlur3ColorProviderTabs = new BlurredBackgroundColorProviderThemed(null, Theme.key_dialogBackground) {
                @Override
                public int getStrokeColorTop() {
                    return isDark() ? 0x06FFFFFF : 0x11000000;
                }

                @Override
                public int getStrokeColorBottom() {
                    return isDark() ? 0x11FFFFFF : 0x20000000;
                }

                @Override
                public int getShadowColor() {
                    return isDark() ? 0x04FFFFFF : 0x20000000;
                }
            };
            iBlur3SourceColor = new GlassSourceColor();
            iBlur3Background = new BlurredBackgroundDrawable(iBlur3SourceColor);
            iBlur3Background.setColorProvider(iBlur3ColorProviderTabs);
            iBlur3Background.setStrokeWidth(dpf2(0.4f), dpf2(0.4f));
            iBlur3Background.setRadius(dp(18));
            iBlur3Background.setPadding(dp(5.66f));
        }

        updateColors();
    }

    // ---------- iOS-style wide "Continue" pill ----------
    private boolean pillMode;
    private int pillCornerPx;
    private CharSequence pillText;
    private TextView pillTextView;
    private int pillBg = 0xFFFFFFFF, pillPressed = 0xFFD1D1D6, pillTextColor = 0xFF000000;
    private int pillBgOff = 0xFF2C2C2E, pillTextOff = 0xFF6C6C70;
    private float pillActive = 1f;
    private ValueAnimator pillActiveAnimator;

    public boolean isPillMode() {
        return pillMode;
    }

    public CharSequence getPillText() {
        return pillText;
    }

    public void setPillMode(CharSequence text, int cornerDp) {
        pillMode = true;
        pillText = text;
        pillCornerPx = dp(cornerDp);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), pillCornerPx);
            }
        });
        setTranslationZ(0);
        if (pillTextView == null) {
            pillTextView = new TextView(getContext());
            pillTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            pillTextView.setTypeface(AndroidUtilities.bold());
            pillTextView.setGravity(Gravity.CENTER);
            pillTextView.setSingleLine(true);
            addView(pillTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            addAdditionalView(pillTextView);
        }
        pillTextView.setText(text);
        themedBackground = null;
        updateColors();
    }

    /** Theme-dependent colours: active bg/pressed/text and the dimmed (inactive) bg/text. */
    public void setPillColors(int bg, int pressed, int text, int bgOff, int textOff) {
        if (pillBg == bg && pillPressed == pressed && pillTextColor == text && pillBgOff == bgOff && pillTextOff == textOff) {
            return;
        }
        pillBg = bg;
        pillPressed = pressed;
        pillTextColor = text;
        pillBgOff = bgOff;
        pillTextOff = textOff;
        if (pillMode) {
            themedBackground = null;
            updateColors();
        }
    }

    public void setPillText(CharSequence text) {
        pillText = text;
        if (pillTextView != null) {
            pillTextView.setText(text);
        }
    }

    /** active = white button, inactive = dark gray (iOS disabled look). Stays clickable on purpose, so tapping shows the field error. */
    public void setPillActive(boolean active, boolean animated) {
        final float to = active ? 1f : 0f;
        if (pillActiveAnimator != null) {
            pillActiveAnimator.cancel();
            pillActiveAnimator = null;
        }
        if (!animated) {
            pillActive = to;
            applyPillActive();
            return;
        }
        if (pillActive == to) {
            return;
        }
        pillActiveAnimator = ValueAnimator.ofFloat(pillActive, to);
        pillActiveAnimator.setDuration(220);
        pillActiveAnimator.addUpdateListener(a -> {
            pillActive = (float) a.getAnimatedValue();
            applyPillActive();
        });
        pillActiveAnimator.start();
    }

    private void applyPillActive() {
        if (!pillMode) {
            return;
        }
        final int bg = ColorUtils.blendARGB(pillBgOff, pillBg, pillActive);
        final int tx = ColorUtils.blendARGB(pillTextOff, pillTextColor, pillActive);
        if (themedBackground != null) {
            Theme.setSelectorDrawableColor(themedBackground, bg, false);
            themedBackground.invalidateSelf();
        }
        if (pillTextView != null) {
            pillTextView.setTextColor(tx);
        }
    }

    public void setProgressVisible(boolean visible, boolean animated) {
        animatorProgressVisible.setValue(visible, animated);
    }

    public void setButtonVisible(boolean visible, boolean animated) {
        animatorButtonVisible.setValue(visible, animated);
    }

    public boolean getButtonVisible() {
        return animatorButtonVisible.getValue();
    }

    public boolean getProgressVisible() {
        return animatorProgressVisible.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_BUTTON_VISIBLE) {
            if (pillMode) {
                setAlpha(factor);
                setScaleX(lerp(0.94f, 1f, factor));
                setScaleY(lerp(0.94f, 1f, factor));
                setVisibility(factor > 0 ? VISIBLE : GONE);
            } else {
                setAnimatedVisibility(this, factor);
            }
            setClickable(factor >= 0.99f);
            setAdditionalTranslationY(dp(isSubButton ? 64 : 40) * (1f - factor));
        } else if (id == ANIMATOR_ID_PROGRESS_VISIBLE) {
            setAnimatedVisibility(progressView, factor);
            setAnimatedVisibility(imageView, 1f - factor);
            if (additionalContentViews != null) {
                for (View v : additionalContentViews) {
                    setAnimatedVisibility(v, 1f - factor);
                }
            }
        }
    }

    public void setAnimation(@RawRes int animation, int size) {
        imageView.setAnimation(animation, size, size);
    }

    public void setImageResource(@DrawableRes int drawable) {
        imageView.setImageResource(drawable);
    }

    private GlassSourceColor iBlur3SourceColor;
    private BlurredBackgroundDrawable iBlur3Background;
    private BlurredBackgroundColorProviderThemed iBlur3ColorProviderTabs;

    private Drawable themedBackground;
    private int themedIconColor;
    private boolean themedIconColorSet;
    private int themedBackgroundColor, themedBackgroundPressedColor;

    @Override
    public void updateColors() {
        if (imageView == null || progressView == null) {
            return;
        }
        final boolean recolorBackground = themedBackground != null && getBackground() == themedBackground;
        if (isSubButton) {
            final int iconColor = Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider);
            if (iconColor != themedIconColor || !themedIconColorSet) {
                themedIconColor = iconColor;
                themedIconColorSet = true;
                imageView.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            }
            progressView.setProgressColor(Theme.getColor(Theme.key_actionBarDefaultIcon, resourcesProvider));

            if (iBlur3SourceColor != null) {
                iBlur3SourceColor.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            if (iBlur3ColorProviderTabs != null) {
                iBlur3ColorProviderTabs.updateColors();
            }
            if (iBlur3Background != null) {
                iBlur3Background.updateColors();
            }
            invalidate();

            int rad = dp(18);
            int pressedColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
            if (recolorBackground) {
                if (pressedColor != themedBackgroundPressedColor) {
                    themedBackgroundPressedColor = pressedColor;
                    Theme.setSelectorDrawableColor(themedBackground, pressedColor, true);
                }
            } else {
                setBackground(themedBackground = Theme.createInsetRoundRectDrawable(themedBackgroundPressedColor = pressedColor, rad, dp(6)));
            }
        } else if (pillMode) {
            progressView.setProgressColor(pillTextColor);
            if (themedBackground == null || getBackground() != themedBackground) {
                setBackground(themedBackground = Theme.createSimpleSelectorRoundRectDrawable(pillCornerPx, pillBg, pillPressed));
            }
            applyPillActive();
        } else {
            final int iconColor = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
            if (iconColor != themedIconColor || !themedIconColorSet) {
                themedIconColor = iconColor;
                themedIconColorSet = true;
                imageView.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            }
            progressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider));
            final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            final int pressedColor = Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourcesProvider);
            if (recolorBackground) {
                if (color != themedBackgroundColor || pressedColor != themedBackgroundPressedColor) {
                    themedBackgroundColor = color;
                    themedBackgroundPressedColor = pressedColor;
                    Theme.setSelectorDrawableColor(themedBackground, color, false);
                    Theme.setSelectorDrawableColor(themedBackground, pressedColor, true);
                    themedBackground.invalidateSelf();
                }
            } else {
                themedBackgroundColor = color;
                themedBackgroundPressedColor = pressedColor;
                setBackground(themedBackground = Theme.createSimpleSelectorCircleDrawable(dp(48), color, pressedColor));
            }
        }
    }

    public static final int SIZE = 48;

    public static FrameLayout.LayoutParams createSubButtonLayoutParams() {
        return LayoutHelper.createFrame(48, 48,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, 14);
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParams() {
        return LayoutHelper.createFrame(48, 48,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20, 0, 20, 14);
    }


    public static FrameLayout.LayoutParams createPillLayoutParams() {
        return LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM, 24, 0, 24, 14);
    }

    public static FrameLayout.LayoutParams createDefaultLayoutParamsBig() {
        return LayoutHelper.createFrame(56, 56,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM,
                20 /*24*/, 0, 20 /*24*/, 14 /*16*/);
    }

    private float additionalTranslationY;
    private float internalTranslationY;
    private boolean setTranslationInternal;

    private void setAdditionalTranslationY(float translationY) {
        if (additionalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(internalTranslationY + translationY);
            setTranslationInternal = false;
            additionalTranslationY = translationY;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (iBlur3Background != null) {
            iBlur3Background.setBounds(0, 0, w, h);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (iBlur3Background != null) {
            iBlur3Background.draw(canvas);
        }
        super.draw(canvas);
    }

    @Override
    public void setTranslationY(float translationY) {
        if (internalTranslationY != translationY) {
            setTranslationInternal = true;
            super.setTranslationY(translationY + additionalTranslationY);
            setTranslationInternal = false;
            internalTranslationY = translationY;
        }
    }

    @Override
    public float getTranslationY() {
        if (setTranslationInternal) {
            return super.getTranslationY();
        }

        return internalTranslationY;
    }

    public void addAdditionalView(View view) {
        if (additionalContentViews == null) {
            additionalContentViews = new ArrayList<>();
        }
        additionalContentViews.add(view);
        setAnimatedVisibility(view, 1f - animatorProgressVisible.getFloatValue());
    }

    public static void setAnimatedVisibility(@Nullable View v, float f) {
        if (v == null) {
            return;
        }

        v.setAlpha(f);
        v.setScaleX(lerp(0.4f, 1f, f));
        v.setScaleY(lerp(0.4f, 1f, f));
        v.setVisibility(f > 0 ? VISIBLE : GONE);
    }
}
