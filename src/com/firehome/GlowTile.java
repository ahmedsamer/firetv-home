package com.firehome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * A tile that can glow (or, Apple-style, cast a soft shadow) around its edges, and sweep a light sheen across itself. The glow is a blurred rounded rectangle drawn once into
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
    // shadow mode: the glow drawn below the tile and fainter, like tvOS's focus shadow
    private float drop;
    private float maxAlpha = 1f;
    // the sheen: a soft diagonal band of light, at -1 (off the left) .. 1 (off the right), NaN when not showing
    private float sheen = Float.NaN;
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();

    GlowTile(Context context, int colour, float spread, float radius) {
        super(context);
        this.colour = colour | 0xFF000000;
        this.spread = spread;
        this.radius = radius;
        setWillNotDraw(false);
    }

    /** Draw the glow as a shadow: `drop` px below the tile, at most `alpha` strong. */
    void setShadow(float drop, float alpha) {
        this.drop = drop;
        this.maxAlpha = alpha;
    }

    /** Sweeps the sheen across the tile once. */
    void sweep() {
        ValueAnimator a = ValueAnimator.ofFloat(-1f, 1f);
        a.setDuration(700);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        a.addUpdateListener(v -> {
            sheen = (float) v.getAnimatedValue();
            invalidate();
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                sheen = Float.NaN;
                invalidate();
            }
        });
        a.start();
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
            paint.setAlpha(Math.round(strength * maxAlpha * 255));
            canvas.drawBitmap(glow, -spread * 2, -spread * 2 + drop, paint);
        }
        super.draw(canvas);
        if (!Float.isNaN(sheen) && getWidth() > 0) {
            float w = getWidth();
            float h = getHeight();
            float x = sheen * (w + h);
            sheenPaint.setShader(new LinearGradient(x, 0, x + h, h,
                    new int[] {0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            clip.reset();
            clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            canvas.drawRect(0, 0, w, h, sheenPaint);
            canvas.restore();
        }
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
