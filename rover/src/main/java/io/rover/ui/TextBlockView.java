package io.rover.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;

import io.rover.model.Alignment;
import io.rover.model.Offset;
import io.rover.model.Unit;

/**
 * Created by ata_n on 2016-07-06.
 */
public class TextBlockView extends BlockView {

    private Spanned mText;
    // TODO: This is to be changed to an inset property at the Block level
    private Offset mTextOffset;
    private Alignment mAlignment;

    private TextPaint mPaint;
    private StaticLayout mLayout;

    public TextBlockView(Context context) {
        super(context);

        mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        if (Build.VERSION.SDK_INT > 20) {
            //mPaint.setElegantTextHeight(true);
        }
        mPaint.setTextSize(49); //Default
        createLayout();
    }

    public void setText(Spanned text) {
        mText = text;
        createLayout();
    }

    public void setTextOffset(Offset offset) {
        mTextOffset = offset;
        createLayout();
    }

    public void setTextSize(float size) {
        mPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size, getResources().getDisplayMetrics()));
        createLayout();
    }

    public void setTypeface(Typeface typeface) {
        mPaint.setTypeface(typeface);
        createLayout();
    }

    public void setTextColor(int color) {
        mPaint.setColor(color);
        createLayout();
    }

    public void setTextAlignment(Alignment alignment) {
        mAlignment = alignment;
        mPaint.setTextAlign(getHorizontalAlignment());
        createLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createLayout();
    }

    private void createLayout() {
        if (mText == null) {
            mText = new SpannedString("");
        }

        int width = getWidth();

        double textWidth = width - getPaddingLeft() - getPaddingRight() - getLeftInset() - getRightInset();
        if (textWidth > 0) {
            mLayout = new StaticLayout(mText, mPaint, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mLayout != null) {
            canvas.save();

            int yPos = 0;
            switch (getVerticalAlignment()) {
                case Middle: {
                    yPos = (canvas.getHeight() / 2) - (mLayout.getHeight() / 2);
                    break;
                }
                case Bottom: {
                    yPos = canvas.getHeight() - mLayout.getHeight();
                    break;
                }
            }

            int xPos = 0;
            switch (getHorizontalAlignment()) {
                case CENTER: {
                    xPos = mLayout.getWidth() / 2;
                    break;
                }
                case RIGHT: {
                    xPos = mLayout.getWidth();
                    break;
                }
            }

            canvas.translate(xPos + getPaddingLeft() + getLeftInset() ,yPos + getPaddingTop() + getTopInset());
            mLayout.draw(canvas);
            canvas.restore();
        }
    }

    private Paint.Align getHorizontalAlignment() {
        if (mAlignment == null) {
            return Paint.Align.LEFT;
        }

        switch (mAlignment.getHorizontal()) {
            case Right: {
                return Paint.Align.RIGHT;
            }
            case Center: {
                return Paint.Align.CENTER;
            }
            default: return Paint.Align.LEFT;
        }
    }

    private Alignment.Vertical getVerticalAlignment() {
        if (mAlignment == null) {
            return Alignment.Vertical.Top;
        }

        if (mAlignment.getVertical() != null) {
            return mAlignment.getVertical();
        }
        return Alignment.Vertical.Top;
    }

    private float getLeftInset() {
        if (mTextOffset != null) {
            return (float) getValueFromUnit(mTextOffset.getLeft());
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) mInset.left, getResources().getDisplayMetrics());
    }

    private float getRightInset() {
        if (mTextOffset != null) {
            return (float) getValueFromUnit(mTextOffset.getRight());
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) mInset.right, getResources().getDisplayMetrics());
    }

    private float getTopInset() {
        if (mTextOffset != null) {
            return (float) getValueFromUnit(mTextOffset.getTop());
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) mInset.top, getResources().getDisplayMetrics());
    }

    private float getBottomInset() {
        if (mTextOffset != null) {
            return (float) getValueFromUnit(mTextOffset.getBottom());
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) mInset.bottom, getResources().getDisplayMetrics());
    }

    private double getValueFromUnit(Unit unit) {
        if (unit != null) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) unit.getValue(), getResources().getDisplayMetrics());
        }
        return 0;
    }
}
