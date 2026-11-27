package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Значок NEXLYGRAM как обычный Drawable.
 * Подходит везде, где Telegram ставит иконку рядом с именем
 * (шапка чата, профиль, списки). Сам анимирует звёздочки.
 */
public class NexlyBadgeDrawable extends Drawable {

    private String type;
    private final int sizePx;

    public NexlyBadgeDrawable(String type, int sizePx) {
        this.type = type;
        this.sizePx = sizePx;
    }

    public void setType(String type) {
        this.type = type;
        invalidateSelf();
    }

    public String getType() {
        return type;
    }

    @Override
    public void draw(Canvas canvas) {
        if (type == null) return;
        Rect b = getBounds();
        float r = Math.min(b.width(), b.height()) / 2f;
        NexlyBadges.draw(canvas, b.exactCenterX(), b.exactCenterY(), r, type);
        scheduleSelf(this::invalidateSelf, android.os.SystemClock.uptimeMillis() + 32);
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
