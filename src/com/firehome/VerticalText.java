package com.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * A TextView turned a quarter left, reading bottom to top, that also lays out as tall and narrow (a rotated view would
 * keep its wide, flat bounds). Single line only. With fitTo() its text is sized so that it runs exactly that long,
 * whatever it says, within MIN_SP..MAX_SP.
 */
class VerticalText extends TextView {
    private static final float MIN_SP = 8;
    private static final float MAX_SP = 26;
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
        // measured at a large size so rounding doesn't matter; letter spacing is in ems, so it scales along
        TextPaint probe = new TextPaint(getPaint());
        probe.setTextSize(100);
        float width = probe.measureText(text, 0, text.length());
        float sp = getResources().getDisplayMetrics().scaledDensity;
        float size = Math.max(MIN_SP * sp, Math.min(MAX_SP * sp, 100 * fitLength / width));
        if (Math.abs(size - getTextSize()) > 0.5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
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
        canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());
        layout.draw(canvas);
        canvas.restore();
    }
}
