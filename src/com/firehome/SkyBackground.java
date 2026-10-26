package com.firehome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * A tvOS-style wallpaper drawn in code: night sky at the top fading to a clear blue, a warm glow low on the
 * horizon, faint stars, and a darker band along the bottom so the app row and its text stay readable.
 */
class SkyBackground extends Drawable {
    private static final int STARS = 90;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader sky;
    private Shader glow;
    private Shader shade;
    private float[] stars;

    @Override
    protected void onBoundsChange(Rect b) {
        float w = b.width();
        float h = b.height();
        sky = new LinearGradient(0, b.top, 0, b.bottom,
                new int[] {0xFF0A1630, 0xFF143A66, 0xFF2D78A8, 0xFF5BA7C9, 0xFF3F7FA0},
                new float[] {0f, 0.35f, 0.62f, 0.8f, 1f}, Shader.TileMode.CLAMP);
        glow = new RadialGradient(b.left + 0.6f * w, b.top + 0.86f * h, 0.42f * w,
                new int[] {0x8CF2B48A, 0x33E39A7A, 0x00E39A7A}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, b.top + 0.45f * h, 0, b.bottom,
                new int[] {0x00000000, 0x33000814, 0xA6000814}, new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        // the same sky every time: a fixed seed
        Random r = new Random(26);
        stars = new float[STARS * 4];
        for (int i = 0; i < STARS; i++) {
            stars[i * 4] = b.left + r.nextFloat() * w;
            stars[i * 4 + 1] = b.top + (float) Math.pow(r.nextFloat(), 1.6) * 0.5f * h;
            stars[i * 4 + 2] = 0.6f + r.nextFloat() * 1.2f;
            stars[i * 4 + 3] = 40 + r.nextInt(120);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        for (Shader shader : new Shader[] {sky, glow}) {
            paint.setShader(shader);
            canvas.drawRect(b, paint);
        }
        star.setColor(0xFFFFFFFF);
        for (int i = 0; i < STARS; i++) {
            star.setAlpha((int) stars[i * 4 + 3]);
            canvas.drawCircle(stars[i * 4], stars[i * 4 + 1], stars[i * 4 + 2], star);
        }
        paint.setShader(shade);
        canvas.drawRect(b, paint);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
