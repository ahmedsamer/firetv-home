package com.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * A TextView turned a quarter left, reading bottom to top, that also lays out as tall and narrow (a rotated view would
 * keep its wide, flat bounds). Single line only. With fitTo() it runs exactly that long whatever it says: every
 * caption gets the same letter size (the one REFERENCE needs at its usual spacing) and the letter spacing makes up
 * the length, so "LIVE" is spaced out rather than drawn bigger. Only a caption too long even at MIN_SPACING gets
 * smaller letters.
 */
class VerticalText extends TextView {
    private static final String REFERENCE = "WATCH NOW";
    private static final float REFERENCE_SPACING = 0.12f;   // ems
    private static final float MIN_SPACING = 0.04f;
    private static final float MAX_SPACING = 3f;   // enough for a four-letter word like "LIVE"
    private int fitLength;   // 0 until fitTo(); also 0 while TextView's constructor sets the first text

    VerticalText(Context context) {
        super(context);
    }

    /** Sizes the text to run `length` pixels from end to end, now and whenever it changes. */
    void fitTo(int length) {
        if (length != fitLength) {
            fitLength = length;
            refit();
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        refit();
    }

    private void refit() {
        CharSequence text = getText();
        if (fitLength <= 0 || text == null || text.length() == 0) {
            return;
        }
        float size = Math.min(sizeFor(REFERENCE, REFERENCE_SPACING), sizeFor(text, MIN_SPACING));
        // the ink grows by `spacing` ems for each gap between letters (Android also pads half a gap at each end, which
        // onMeasure and onDraw trim), so solve for the spacing that fills the length
        TextPaint probe = new TextPaint(getPaint());
        probe.setTextSize(size);
        probe.setLetterSpacing(0);
        float bare = probe.measureText(text, 0, text.length());
        float spacing = text.length() < 2 ? 0 : (fitLength - bare) / ((text.length() - 1) * size);
        spacing = Math.max(MIN_SPACING, Math.min(MAX_SPACING, spacing));
        if (Math.abs(size - getTextSize()) > 0.25f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
        if (Math.abs(spacing - getLetterSpacing()) > 0.001f) {
            setLetterSpacing(spacing);
        }
    }

    /** The half gap Android's letter spacing adds before the first letter and after the last. */
    private float endPadding() {
        return getLetterSpacing() * getTextSize() / 2;
    }

    /** The text size at which `text` runs fitLength with `spacing` ems between letters. */
    private float sizeFor(CharSequence text, float spacing) {
        TextPaint probe = new TextPaint(getPaint());
        probe.setTextSize(100);   // measured large, so rounding doesn't matter
        probe.setLetterSpacing(spacing);
        return 100 * fitLength / (probe.measureText(text, 0, text.length()) - spacing * 100);   // ink, without the ends
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth() - Math.round(endPadding() * 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null) {
            return;
        }
        getPaint().setColor(getCurrentTextColor());
        getPaint().drawableState = getDrawableState();
        canvas.save();
        canvas.translate(0, getHeight());
        canvas.rotate(-90);
        canvas.translate(getCompoundPaddingLeft() - endPadding(), getExtendedPaddingTop());
        layout.draw(canvas);
        canvas.restore();
    }
}
