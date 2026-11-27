package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Значок NEXLYGRAM внутри текста (после имени).
 * Работает везде, где имя рисуется как текст: шапка чата, профиль,
 * списки, поиск, имя автора в сообщениях.
 */
public class NexlyBadgeSpan extends ReplacementSpan {

    private final String type;
    private final int size;
    private final WeakReference<View> parent;

    public NexlyBadgeSpan(String type, View parent, int sizePx) {
        this.type = type;
        this.size = sizePx;
        this.parent = parent != null ? new WeakReference<>(parent) : null;
    }

    /** Сколько места занимает значок вместе с отступом. */
    public static int width(int sizePx) {
        return sizePx + AndroidUtilities.dp(5);
    }

    /** Добавляет значок в конец текста. Если type == null, возвращает текст как есть. */
    public static CharSequence append(CharSequence text, String type, View parent, int sizePx) {
        if (text == null || type == null) {
            return text;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int start = sb.length();
        sb.append(' ');
        sb.setSpan(new NexlyBadgeSpan(type, parent, sizePx), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Нажатие на view показывает описание значка (или снимает обработчик, если значка нет). */
    public static void setupClick(View view, String type) {
        if (view == null) return;
        if (type != null) {
            view.setOnClickListener(v -> NexlyBadges.showInfo(v.getContext(), type));
        } else {
            view.setOnClickListener(null);
            view.setClickable(false);
        }
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            Paint.FontMetricsInt p = paint.getFontMetricsInt();
            fm.ascent = p.ascent;
            fm.descent = p.descent;
            fm.top = p.top;
            fm.bottom = p.bottom;
        }
        return width(size);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float r = size / 2f;
        float cy = y + (paint.ascent() + paint.descent()) / 2f;
        NexlyBadges.draw(canvas, x + AndroidUtilities.dp(4) + r, cy, r, type);
        View v = parent != null ? parent.get() : null;
        if (v != null) {
            v.postInvalidateDelayed(50);
        }
    }
}
