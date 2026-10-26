package com.firehome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * A tile that can glow in its app's colour around its edges. The glow is a blurred rounded rectangle drawn once into
 * a bitmap (blur needs software drawing) and cached per colour and size, then faded in and out on focus. It draws
 * outside the tile's bounds, so the parents must not clip their children.
 */
class GlowTile extends FrameLayout {
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int colour;
    private final float spread;
    private final float radius;
    private Bitmap glow;
    private float strength;
    private ValueAnimator fade;

    GlowTile(Context context, int colour, float spread, float radius) {
        super(context);
        this.colour = colour | 0xFF000000;
        this.spread = spread;
        this.radius = radius;
        setWillNotDraw(false);
    }

    void setGlowing(boolean on) {
        if (fade != null) {
            fade.cancel();
        }
        fade = ValueAnimator.ofFloat(strength, on ? 1f : 0f);
        fade.setDuration(220);
        fade.addUpdateListener(a -> {
            strength = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.start();
    }

    @Override
    public void draw(Canvas canvas) {
        if (strength > 0f && getWidth() > 0) {
            if (glow == null) {
                glow = glowFor(getWidth(), getHeight());
            }
            paint.setAlpha(Math.round(strength * 255));
            canvas.drawBitmap(glow, -spread * 2, -spread * 2, paint);
        }
        super.draw(canvas);
    }

    private Bitmap glowFor(int w, int h) {
        String key = colour + ":" + w + "x" + h;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int pad = Math.round(spread * 2);
        Bitmap bitmap = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888);
        Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
        blur.setColor(colour);
        blur.setMaskFilter(new BlurMaskFilter(spread, BlurMaskFilter.Blur.NORMAL));
        // start a little outside the tile's edge, so the colour reaches past its bright rim
        float grow = spread / 3;
        new Canvas(bitmap).drawRoundRect(new RectF(pad - grow, pad - grow, pad + w + grow, pad + h + grow),
                radius + grow, radius + grow, blur);
        CACHE.put(key, bitmap);
        return bitmap;
    }
}
