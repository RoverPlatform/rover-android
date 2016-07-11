package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-06-16.
 */
public abstract class Block implements Parcelable {

    /** Appearance
     */

    private int mBackgroundColor;
    private int mBorderColor;
    private double mBorderRadius;
    private double mBorderWidth;

    /** Layout
     */

    public enum Position {
        Stacked, Floating
    }

    private Position mPosition;
    private Unit mHeight;
    private Unit mWidth;
    private Alignment mAlignment;
    private Offset mOffset;

    public Block() {
        mOffset = Offset.ZeroOffset;
        mPosition = Position.Stacked;
    }

    public Position getPosition() { return mPosition; }

    public void setPosition(Position position) { mPosition = position; }

    public Unit getHeight() { return mHeight; }

    public void setHeight(Unit height) { mHeight = height; }

    public Unit getWidth() { return mWidth; }

    public void setWidth(Unit width) { mWidth = width; }

    public Alignment getAlignment() { return mAlignment; }

    public void setAlignment(Alignment alignment) { mAlignment = alignment; }

    public Offset getOffset() { return mOffset; }

    public void setOffset(Offset offset) { mOffset = offset; }

    public int getBackgroundColor() { return mBackgroundColor; }

    public void setBackgroundColor(int color) { mBackgroundColor = color; }

    public int getBorderColor() { return mBorderColor; }

    public void setBorderColor(int color) { mBorderColor = color; }

    public double getBorderRadius() { return mBorderRadius; }

    public void setBorderRadius(double radius) { mBorderRadius = radius; }

    public double getBorderWidth() { return mBorderWidth; }

    public void setBorderWidth(double width) { mBorderWidth = width; }

    // TODO: Appearance

    /** Parcelable
     */

    protected Block(Parcel in) {
        mPosition = (Position) in.readSerializable();
        mHeight = (Unit) in.readValue(Unit.class.getClassLoader());
        mWidth = (Unit) in.readValue(Unit.class.getClassLoader());
        mAlignment = (Alignment) in.readValue(Alignment.class.getClassLoader());
        mOffset = (Offset) in.readValue(Offset.class.getClassLoader());
        mBackgroundColor = in.readInt();
        mBorderColor = in.readInt();
        mBorderRadius = in.readDouble();
        mBorderWidth = in.readDouble();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mPosition);
        dest.writeValue(mHeight);
        dest.writeValue(mWidth);
        dest.writeValue(mAlignment);
        dest.writeValue(mOffset);
        dest.writeInt(mBackgroundColor);
        dest.writeInt(mBorderColor);
        dest.writeDouble(mBorderRadius);
        dest.writeDouble(mBorderWidth);
    }
}
