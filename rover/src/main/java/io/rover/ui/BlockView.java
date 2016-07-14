package io.rover.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

/**
 * Created by ata_n on 2016-07-08.
 */
public class BlockView extends LinearLayout {

    private RectF mPathRect;
    private Path mPath;
    private GradientDrawable mBorderDrawable;
    private GradientDrawable mBackgroundDrawable;
    private float mCornerRadius;

    public BlockView(Context context) {
        super(context);

        mCornerRadius = 0;

        mPathRect = new RectF();
        mPathRect.left = 0;
        mPathRect.top = 0;

        mPath = new Path();

        mBorderDrawable = new GradientDrawable();

        mBackgroundDrawable = new GradientDrawable();
        setBackground(mBackgroundDrawable);
    }

    public void setBackgroundColor(int color) {
        mBackgroundDrawable.setColor(color);
    }

    public void setBorder(float borderWidth, int borderColor) {
        mBorderDrawable.setStroke((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, borderWidth, getResources().getDisplayMetrics()), borderColor);
    }

    public void setCornerRadius(float radius) {
        mCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radius, getResources().getDisplayMetrics());
        mBorderDrawable.setCornerRadius(mCornerRadius);
        mBackgroundDrawable.setCornerRadius(mCornerRadius);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mPath.reset();
        mPathRect.bottom = h;
        mPathRect.right = w;
        mPath.addRoundRect(mPathRect, mCornerRadius, mCornerRadius, Path.Direction.CW);

        mBorderDrawable.setBounds(0,0,w,h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.clipPath(mPath);
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        mBorderDrawable.draw(canvas);
    }
}
