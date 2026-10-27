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
 * A tvOS-style wallpaper drawn in code: a sky gradient, a soft glow low on the horizon, stars, and a darker band
 * along the bottom so the app row and its text stay readable. Four looks, for the phases of the day (see Sun):
 * night (deep and starry), morning (pale dawn), day (clear, no stars) and evening (a warm glow low down). All are
 * tinted cyan/teal to match the room's furniture.
 */
class SkyBackground extends Drawable {
    enum Phase {
        //        sky, top to bottom                                                     glow       stars bottom band
        NIGHT(new int[] {0xFF03121A, 0xFF062838, 0xFF0B4352, 0xFF136270, 0xFF0F515E}, 0x5970E6E0, 140, 0xB3000E14),
        MORNING(new int[] {0xFF1B4658, 0xFF3A7D8C, 0xFF86C2C4, 0xFFD6E8DA, 0xFFB4D4CA}, 0x80FFEFD0, 0, 0xA6082028),
        DAY(new int[] {0xFF0A7484, 0xFF1A92A2, 0xFF4CB6C0, 0xFF8AD4D8, 0xFF68C0C6}, 0x66F0FFF8, 0, 0xB3032830),
        EVENING(new int[] {0xFF051824, 0xFF0B3A4A, 0xFF1C707E, 0xFF4AA6A6, 0xFF36888A}, 0x8CF2BC8A, 90, 0xA6000E14);

        final int[] sky;
        final int glow;
        final int stars;
        final int band;

        Phase(int[] sky, int glow, int stars, int band) {
            this.sky = sky;
            this.glow = glow;
            this.stars = stars;
            this.band = band;
        }
    }

    private final Phase phase;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader sky;
    private Shader glow;
    private Shader shade;
    private float[] stars;

    SkyBackground(Phase phase) {
        this.phase = phase;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        float w = b.width();
        float h = b.height();
        sky = new LinearGradient(0, b.top, 0, b.bottom, phase.sky,
                new float[] {0f, 0.35f, 0.62f, 0.8f, 1f}, Shader.TileMode.CLAMP);
        glow = new RadialGradient(b.left + 0.6f * w, b.top + 0.86f * h, 0.42f * w,
                new int[] {phase.glow, alpha(phase.glow, 0.4f), alpha(phase.glow, 0f)},
                new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        shade = new LinearGradient(0, b.top + 0.45f * h, 0, b.bottom,
                new int[] {alpha(phase.band, 0f), alpha(phase.band, 0.3f), phase.band}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP);
        // the same sky every time: a fixed seed
        Random r = new Random(26);
        stars = new float[phase.stars * 4];
        for (int i = 0; i < phase.stars; i++) {
            stars[i * 4] = b.left + r.nextFloat() * w;
            stars[i * 4 + 1] = b.top + (float) Math.pow(r.nextFloat(), 1.6) * 0.5f * h;
            stars[i * 4 + 2] = 0.6f + r.nextFloat() * 1.2f;
            stars[i * 4 + 3] = 40 + r.nextInt(phase == Phase.NIGHT ? 170 : 120);
        }
    }

    /** The colour with its alpha scaled by f. */
    private static int alpha(int colour, float f) {
        return (Math.round((colour >>> 24) * f) << 24) | (colour & 0xFFFFFF);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        for (Shader shader : new Shader[] {sky, glow}) {
            paint.setShader(shader);
            canvas.drawRect(b, paint);
        }
        star.setColor(0xFFFFFFFF);
        for (int i = 0; i < phase.stars; i++) {
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
