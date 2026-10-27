package com.firehome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Line icons for the Home Assistant bar, drawn in code: a bulb, a fan, a blind and a curtain. "On" (or open) draws
 * them in the accent colour (dark, for the bar's yellow "on" button) with a fill; off draws them as white outlines. A blind or curtain shows how far open it
 * is through `level` (0 closed .. 1 open).
 */
class BarIcon extends Drawable {
    enum Kind { LIGHT, FAN, BLIND, CURTAIN }

    private final Kind kind;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean on;
    private float level;
    private int accent = 0xFFFFC857;

    BarIcon(Kind kind) {
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(boolean on, float level, int accent) {
        this.on = on;
        this.level = Math.max(0f, Math.min(1f, level));
        this.accent = accent;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        canvas.save();
        canvas.translate(b.left + (b.width() - s) / 2, b.top + (b.height() - s) / 2);
        int colour = on ? accent : 0xE6FFFFFF;
        line.setColor(colour);
        line.setStrokeWidth(s * 0.075f);
        fill.setColor(colour);
        switch (kind) {
            case LIGHT:
                Path bulb = new Path();
                bulb.addCircle(s * 0.5f, s * 0.4f, s * 0.26f, Path.Direction.CW);
                if (on) {
                    fill.setAlpha(200);
                    canvas.drawPath(bulb, fill);
                }
                canvas.drawPath(bulb, line);
                canvas.drawLine(s * 0.38f, s * 0.78f, s * 0.62f, s * 0.78f, line);
                canvas.drawLine(s * 0.42f, s * 0.9f, s * 0.58f, s * 0.9f, line);
                break;
            case FAN:
                canvas.drawCircle(s * 0.5f, s * 0.5f, s * 0.07f, fill);
                for (int i = 0; i < 3; i++) {
                    canvas.save();
                    canvas.rotate(i * 120f + (on ? 20f : 0f), s * 0.5f, s * 0.5f);
                    Path blade = new Path();
                    blade.moveTo(s * 0.5f, s * 0.44f);
                    blade.cubicTo(s * 0.34f, s * 0.3f, s * 0.38f, s * 0.08f, s * 0.54f, s * 0.08f);
                    blade.cubicTo(s * 0.66f, s * 0.12f, s * 0.62f, s * 0.32f, s * 0.5f, s * 0.44f);
                    if (on) {
                        fill.setAlpha(220);
                        canvas.drawPath(blade, fill);
                    } else {
                        canvas.drawPath(blade, line);
                    }
                    canvas.restore();
                }
                break;
            case BLIND:
                canvas.drawLine(s * 0.12f, s * 0.14f, s * 0.88f, s * 0.14f, line);
                // slats cover the closed part; an open blind keeps only its top slats
                float bottom = s * (0.86f - 0.62f * level);
                for (float y = s * 0.28f; y <= bottom + 1; y += s * 0.14f) {
                    canvas.drawLine(s * 0.2f, y, s * 0.8f, y, line);
                }
                canvas.drawLine(s * 0.5f, s * 0.14f, s * 0.5f, Math.max(bottom, s * 0.28f), line);
                break;
            case CURTAIN:
                canvas.drawLine(s * 0.1f, s * 0.14f, s * 0.9f, s * 0.14f, line);
                float panel = s * (0.38f - 0.24f * level);
                RectF left = new RectF(s * 0.12f, s * 0.2f, s * 0.12f + panel, s * 0.88f);
                RectF right = new RectF(s * 0.88f - panel, s * 0.2f, s * 0.88f, s * 0.88f);
                float r = s * 0.05f;
                if (on) {
                    fill.setAlpha(160);
                    canvas.drawRoundRect(left, r, r, fill);
                    canvas.drawRoundRect(right, r, r, fill);
                }
                canvas.drawRoundRect(left, r, r, line);
                canvas.drawRoundRect(right, r, r, line);
                break;
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
