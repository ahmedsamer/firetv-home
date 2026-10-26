package com.firehome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Weather symbols drawn in code, in the spirit of Apple's weather icons: a yellow sun or a pale moon, white clouds,
 * blue rain, white snow, a yellow bolt. No image files, and they stay sharp at any size.
 */
class WeatherIcon extends Drawable {
    enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

    private static final int SUN = 0xFFFFD54A;
    private static final int MOON = 0xFFECEAF4;
    private static final int CLOUD = 0xFFFFFFFF;
    private static final int RAIN = 0xFF7CC4FF;

    private final Kind kind;
    private final boolean day;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    WeatherIcon(Kind kind, boolean day) {
        this.kind = kind;
        this.day = day;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setShadowLayer(4, 0, 1, 0x55000000);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        canvas.save();
        canvas.translate(b.left + (b.width() - s) / 2, b.top + (b.height() - s) / 2);
        line.setStrokeWidth(s * 0.07f);
        switch (kind) {
            case CLEAR:
                if (day) {
                    sun(canvas, s * 0.5f, s * 0.5f, s * 0.42f);
                } else {
                    moon(canvas, s * 0.5f, s * 0.5f, s * 0.36f);
                }
                break;
            case PARTLY:
                if (day) {
                    sun(canvas, s * 0.36f, s * 0.36f, s * 0.3f);
                } else {
                    moon(canvas, s * 0.38f, s * 0.36f, s * 0.24f);
                }
                cloud(canvas, s * 0.18f, s * 0.42f, s * 0.95f, s * 0.86f);
                break;
            case CLOUDY:
                cloud(canvas, s * 0.04f, s * 0.22f, s * 0.96f, s * 0.8f);
                break;
            case FOG:
                cloud(canvas, s * 0.1f, s * 0.08f, s * 0.9f, s * 0.56f);
                line.setColor(CLOUD);
                for (int i = 0; i < 2; i++) {
                    float y = s * (0.7f + i * 0.16f);
                    canvas.drawLine(s * (0.16f + i * 0.08f), y, s * (0.84f - i * 0.08f), y, line);
                }
                break;
            case RAIN:
                cloud(canvas, s * 0.06f, s * 0.06f, s * 0.94f, s * 0.6f);
                line.setColor(RAIN);
                for (int i = 0; i < 3; i++) {
                    float x = s * (0.32f + i * 0.2f);
                    canvas.drawLine(x, s * 0.7f, x - s * 0.06f, s * 0.9f, line);
                }
                break;
            case SNOW:
                cloud(canvas, s * 0.06f, s * 0.06f, s * 0.94f, s * 0.6f);
                fill.setColor(CLOUD);
                for (int i = 0; i < 3; i++) {
                    canvas.drawCircle(s * (0.3f + i * 0.2f), s * (i % 2 == 0 ? 0.76f : 0.88f), s * 0.05f, fill);
                }
                break;
            case THUNDER:
                cloud(canvas, s * 0.06f, s * 0.04f, s * 0.94f, s * 0.58f);
                Path bolt = new Path();
                bolt.moveTo(s * 0.54f, s * 0.52f);
                bolt.lineTo(s * 0.38f, s * 0.76f);
                bolt.lineTo(s * 0.5f, s * 0.76f);
                bolt.lineTo(s * 0.42f, s * 0.97f);
                bolt.lineTo(s * 0.66f, s * 0.68f);
                bolt.lineTo(s * 0.53f, s * 0.68f);
                bolt.lineTo(s * 0.62f, s * 0.52f);
                bolt.close();
                fill.setColor(SUN);
                canvas.drawPath(bolt, fill);
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas canvas, float cx, float cy, float r) {
        fill.setColor(SUN);
        canvas.drawCircle(cx, cy, r * 0.55f, fill);
        line.setColor(SUN);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            canvas.drawLine(cx + cos * r * 0.75f, cy + sin * r * 0.75f, cx + cos * r, cy + sin * r, line);
        }
    }

    private void moon(Canvas canvas, float cx, float cy, float r) {
        Path disc = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(cx + r * 0.5f, cy - r * 0.35f, r * 0.82f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(MOON);
        canvas.drawPath(disc, fill);
    }

    private void cloud(Canvas canvas, float l, float t, float r, float bottom) {
        float w = r - l;
        float h = bottom - t;
        Path cloud = new Path();
        cloud.addRoundRect(l, t + h * 0.45f, r, bottom, h * 0.28f, h * 0.28f, Path.Direction.CW);
        Path puff = new Path();
        puff.addCircle(l + w * 0.32f, t + h * 0.52f, h * 0.3f, Path.Direction.CW);
        cloud.op(puff, Path.Op.UNION);
        puff.reset();
        puff.addCircle(l + w * 0.58f, t + h * 0.4f, h * 0.4f, Path.Direction.CW);
        cloud.op(puff, Path.Op.UNION);
        fill.setColor(CLOUD);
        canvas.drawPath(cloud, fill);
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
