package com.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.widget.TextView;

/**
 * A TextView turned a quarter left, reading bottom to top, that also lays out as tall and narrow (a rotated view would
 * keep its wide, flat bounds). Single line only.
 */
class VerticalText extends TextView {
    VerticalText(Context context) {
        super(context);
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
