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
 * Line icons for the Home Assistant bar, drawn in code: devices (a bulb, a ceiling pendant, a desk, a bed, a fan, a
 * blind, a curtain) and rooms (a sofa, a cooking pot, a bathtub, a door), and a house for the indoor climate. "On" (or open) draws
 * them in the accent colour (dark, for the bar's yellow "on" button) with a fill; off draws them as white outlines. A blind or curtain shows how far open it
 * is through `level` (0 closed .. 1 open).
 */
class BarIcon extends Drawable {
    enum Kind { LIGHT, CEILING, DESK, BED, FAN, BLIND, CURTAIN, SOFA, KITCHEN, BATH, DOOR, HOME }

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
            case CEILING: {
                canvas.drawLine(s * 0.5f, s * 0.04f, s * 0.5f, s * 0.3f, line);
                Path shade = new Path();
                shade.moveTo(s * 0.14f, s * 0.64f);
                shade.cubicTo(s * 0.16f, s * 0.36f, s * 0.84f, s * 0.36f, s * 0.86f, s * 0.64f);
                shade.close();
                if (on) {
                    fill.setAlpha(220);
                    canvas.drawPath(shade, fill);
                }
                canvas.drawPath(shade, line);
                canvas.drawCircle(s * 0.5f, s * 0.74f, s * 0.08f, on ? fill : line);
                break;
            }
            case DESK: {
                // a desk with a monitor on it: top, two legs, a drawer on the right
                RectF screen = new RectF(s * 0.3f, s * 0.12f, s * 0.7f, s * 0.42f);
                float r = s * 0.04f;
                if (on) {
                    fill.setAlpha(220);
                    canvas.drawRoundRect(screen, r, r, fill);
                }
                canvas.drawRoundRect(screen, r, r, line);
                canvas.drawLine(s * 0.5f, s * 0.42f, s * 0.5f, s * 0.52f, line);
                canvas.drawLine(s * 0.06f, s * 0.54f, s * 0.94f, s * 0.54f, line);
                canvas.drawLine(s * 0.14f, s * 0.54f, s * 0.14f, s * 0.9f, line);
                canvas.drawRect(s * 0.6f, s * 0.54f, s * 0.86f, s * 0.9f, line);
                canvas.drawLine(s * 0.68f, s * 0.68f, s * 0.78f, s * 0.68f, line);
                break;
            }
            case BED: {
                // side view: headboard, mattress with a pillow, legs
                canvas.drawLine(s * 0.1f, s * 0.24f, s * 0.1f, s * 0.86f, line);
                RectF mattress = new RectF(s * 0.1f, s * 0.52f, s * 0.9f, s * 0.72f);
                if (on) {
                    fill.setAlpha(220);
                    canvas.drawRect(mattress, fill);
                }
                canvas.drawRect(mattress, line);
                RectF pillow = new RectF(s * 0.18f, s * 0.38f, s * 0.42f, s * 0.52f);
                float r = s * 0.06f;
                if (on) {
                    canvas.drawRoundRect(pillow, r, r, fill);
                }
                canvas.drawRoundRect(pillow, r, r, line);
                canvas.drawLine(s * 0.9f, s * 0.72f, s * 0.9f, s * 0.86f, line);
                break;
            }
            case SOFA: {
                float r = s * 0.06f;
                canvas.drawRoundRect(new RectF(s * 0.2f, s * 0.26f, s * 0.8f, s * 0.56f), r, r, line);
                canvas.drawRoundRect(new RectF(s * 0.08f, s * 0.44f, s * 0.92f, s * 0.74f), r, r, line);
                canvas.drawLine(s * 0.16f, s * 0.74f, s * 0.16f, s * 0.86f, line);
                canvas.drawLine(s * 0.84f, s * 0.74f, s * 0.84f, s * 0.86f, line);
                break;
            }
            case KITCHEN: {
                // a cooking pot with a lid and handles, steam above
                float r = s * 0.06f;
                canvas.drawRoundRect(new RectF(s * 0.2f, s * 0.46f, s * 0.8f, s * 0.86f), r, r, line);
                canvas.drawLine(s * 0.14f, s * 0.4f, s * 0.86f, s * 0.4f, line);
                canvas.drawLine(s * 0.06f, s * 0.54f, s * 0.2f, s * 0.54f, line);
                canvas.drawLine(s * 0.8f, s * 0.54f, s * 0.94f, s * 0.54f, line);
                canvas.drawLine(s * 0.42f, s * 0.1f, s * 0.42f, s * 0.26f, line);
                canvas.drawLine(s * 0.58f, s * 0.1f, s * 0.58f, s * 0.26f, line);
                break;
            }
            case BATH: {
                // a bathtub with feet and a tap rising from its left end
                Path tub = new Path();
                tub.moveTo(s * 0.08f, s * 0.5f);
                tub.lineTo(s * 0.92f, s * 0.5f);
                tub.lineTo(s * 0.84f, s * 0.76f);
                tub.lineTo(s * 0.16f, s * 0.76f);
                tub.close();
                canvas.drawPath(tub, line);
                canvas.drawLine(s * 0.22f, s * 0.76f, s * 0.2f, s * 0.88f, line);
                canvas.drawLine(s * 0.78f, s * 0.76f, s * 0.8f, s * 0.88f, line);
                canvas.drawLine(s * 0.2f, s * 0.5f, s * 0.2f, s * 0.16f, line);
                canvas.drawLine(s * 0.2f, s * 0.16f, s * 0.38f, s * 0.16f, line);
                break;
            }
            case DOOR: {
                canvas.drawRect(s * 0.26f, s * 0.08f, s * 0.74f, s * 0.92f, line);
                canvas.drawCircle(s * 0.62f, s * 0.52f, s * 0.05f, fill);
                canvas.drawLine(s * 0.12f, s * 0.92f, s * 0.88f, s * 0.92f, line);
                break;
            }
            case HOME: {
                // a house: roof, walls, a door
                Path house = new Path();
                house.moveTo(s * 0.08f, s * 0.48f);
                house.lineTo(s * 0.5f, s * 0.1f);
                house.lineTo(s * 0.92f, s * 0.48f);
                canvas.drawPath(house, line);
                Path walls = new Path();
                walls.moveTo(s * 0.2f, s * 0.38f);
                walls.lineTo(s * 0.2f, s * 0.9f);
                walls.lineTo(s * 0.8f, s * 0.9f);
                walls.lineTo(s * 0.8f, s * 0.38f);
                canvas.drawPath(walls, line);
                canvas.drawRect(s * 0.42f, s * 0.62f, s * 0.58f, s * 0.9f, line);
                break;
            }
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
