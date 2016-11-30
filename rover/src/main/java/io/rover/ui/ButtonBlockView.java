package io.rover.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

import io.rover.model.Alignment;
import io.rover.model.Font;
import io.rover.model.Offset;

/**
 * Created by ata_n on 2016-07-11.
 */
public class ButtonBlockView extends TextBlockView {

    public enum State {
        Normal, Highlighted, Selected, Disabled
    }

    private State mCurrentState;
    private Map<State, String> mTitles;
    private Map<State, Integer> mTitleColors;
    private Map<State, Alignment> mTitleAlignments;
    private Map<State, Offset> mTitleOffsets;
    private Map<State, Typeface> mTitleTypefaces;
    private Map<State, Integer> mBackgroundColors;
    private Map<State, Integer> mBorderColors;
    private Map<State, Float> mBorderWidths;
    private Map<State, Float> mCornerRadii;
    private Map<State, Float> mTitleTextSizes;

    public ButtonBlockView(Context context) {
        super(context);
        mTitles = new HashMap<>();
        mTitles.put(State.Normal, "");
        mTitleColors = new HashMap<>();
        mTitleColors.put(State.Normal, Color.BLACK);
        mTitleAlignments = new HashMap<>();
        mTitleAlignments.put(State.Normal, new Alignment(Alignment.Horizontal.Center, Alignment.Vertical.Middle));
        mTitleOffsets = new HashMap<>();
        mTitleTypefaces = new HashMap<>();
        mTitleTypefaces.put(State.Normal, (new Font(12, 400).getTypeface()));
        mBackgroundColors = new HashMap<>();
        mBackgroundColors.put(State.Normal, Color.TRANSPARENT);
        mBorderColors = new HashMap<>();
        mBorderColors.put(State.Normal, Color.TRANSPARENT);
        mBorderWidths = new HashMap<>();
        mBorderWidths.put(State.Normal, 0.0f);
        mCornerRadii = new HashMap<>();
        mCornerRadii.put(State.Normal, 0.0f);
        mTitleTextSizes = new HashMap<>();
        mTitleTextSizes.put(State.Normal, 12.0f);
        setState(State.Normal);
    }

    public void setTitle(String title, State state) {
        if (title == null) { return; }
        mTitles.put(state, title);
        updateTitle();
    }

    public void setTitleColor(int color, State state) {
        mTitleColors.put(state, color);
        updateTitleColor();
    }

    public void setTitleAlignment(Alignment alignment, State state) {
        if (alignment == null) { return; }
        mTitleAlignments.put(state, alignment);
        updateTitleAlignment();
    }

    public void setTitleOffset(Offset offset, State state) {
        if (offset == null) { return; }
        mTitleOffsets.put(state, offset);
        updateTitleOffset();
    }

    public void setTitleTypeface(Typeface typeface, State state) {
        if (typeface == null) { return; }
        mTitleTypefaces.put(state, typeface);
        updateTitleTypeface();
    }

    public void setBackgroundColor(int color, State state) {
        mBackgroundColors.put(state, color);
        updateBackgroundColor();
    }

    public void setBorder(float width, int color, State state) {
        mBorderColors.put(state, color);
        mBorderWidths.put(state, width);
        updateBorder();
    }

    public void setCornerRadius(float radius, State state) {
        mCornerRadii.put(state, radius);
        updateCornerRadius();
    }

    public void setTitleTextSize(float size, State state) {
        mTitleTextSizes.put(state, size);
        updateTitleTextSize();
    }

    private void updateTitle() {
        String title = mTitles.get(mCurrentState);
        if (title == null) {
            title = mTitles.get(State.Normal);
        }
        super.setText(new SpannableString(title));
    }

    private void updateTitleColor() {
        Integer color = mTitleColors.get(mCurrentState);
        if (color == null) {
            color = mTitleColors.get(State.Normal);
        }
        super.setTextColor(color);
    }

    private void updateTitleAlignment() {
        Alignment alignment = mTitleAlignments.get(mCurrentState);
        if (alignment == null) {
            alignment = mTitleAlignments.get(State.Normal);
        }
        super.setTextAlignment(alignment);
    }

    private void updateTitleOffset() {
        Offset offset = mTitleOffsets.get(mCurrentState);
        if (offset == null) {
            offset = mTitleOffsets.get(State.Normal);
        }
        super.setTextOffset(offset);
    }

    private void updateTitleTypeface() {
        Typeface typeface = mTitleTypefaces.get(mCurrentState);
        if (typeface == null) {
            typeface = mTitleTypefaces.get(State.Normal);
        }
        super.setTypeface(typeface);
    }

    private void updateBackgroundColor() {
        Integer color = mBackgroundColors.get(mCurrentState);
        if (color == null) {
            color = mBackgroundColors.get(State.Normal);
        }
        super.setBackgroundColor(color);
    }

    private void updateBorder() {
        Integer color = mBorderColors.get(mCurrentState);
        if (color == null) {
            color = mBorderColors.get(State.Normal);
        }
        Float width = mBorderWidths.get(mCurrentState);
        if (width == null) {
            width = mBorderWidths.get(State.Normal);
        }
        super.setBorder(width, color);
    }

    private void updateCornerRadius() {
        Float radius = mCornerRadii.get(mCurrentState);
        if (radius == null) {
            radius = mCornerRadii.get(State.Normal);
        }
        super.setCornerRadius(radius);
    }

    private void updateTitleTextSize() {
        Float size = mTitleTextSizes.get(mCurrentState);
        if (size == null) {
            size = mTitleTextSizes.get(State.Normal);
        }
        super.setTextSize(size);
    }

    private void setState(State state) {
        mCurrentState = state;
        updateTitle();
        updateTitleAlignment();
        updateTitleColor();
        updateTitleOffset();
        updateTitleTypeface();
        updateBackgroundColor();
        updateBorder();
        updateCornerRadius();
        updateTitleTextSize();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setState(enabled ? State.Normal : State.Disabled);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mCurrentState == State.Disabled) {
            return false;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                setState(State.Highlighted);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                setState(State.Normal);
                callOnClick();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                setState(State.Normal);
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void setBackgroundColor(int color) { /* Do nothing */ }

    @Override
    public void setBorder(float borderWidth, int borderColor) { /* Do nothing */ }

    @Override
    public void setCornerRadius(float radius) { /* Do nothing */ }

    @Override
    public void setText(Spanned text) { /* Do nothing */ }

    @Override
    public void setTextAlignment(Alignment alignment) { /* Do nothing */ }

    @Override
    public void setTextColor(int color) { /* Do nothing */ }

    @Override
    public void setTextOffset(Offset offset) { /* Do nothing */ }

    @Override
    public void setTextSize(float size) { /* Do nothing */ }

    @Override
    public void setTypeface(Typeface typeface) { /* Do nothing */ }
}
