package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-06-16.
 */
public class Block implements Parcelable {

    /*
        Appearance
     */

    private String mId;
    private int mBackgroundColor;
    private int mBorderColor;
    private double mBorderRadius;
    private double mBorderWidth;
    private Inset mInset;
    private Image mBackgroundImage;
    private Image.ContentMode mBackgroundContentMode;
    private double mBackgroundScale;
    private CustomKeys mCustomKeys = new CustomKeys(0);

    /*
        Layout
     */

    public enum Position {
        Stacked, Floating
    }

    private Position mPosition;
    private Unit mHeight;
    private Unit mWidth;
    private Alignment mAlignment;
    private Offset mOffset;
    private Action mAction;
    private double mOpacity = 1;

    public Block() {
        mOffset = Offset.ZeroOffset;
        mPosition = Position.Stacked;
    }

    public Position getPosition() { return mPosition; }

    public String getId() { return mId; }

    public void setId(String id) { mId = id; }

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

    public Inset getInset() {return  mInset;}

    public void setInset(Inset inset) { mInset = inset; }

    public Action getAction() { return mAction; }

    public void setAction(Action action) { mAction = action; }

    public double getOpacity() { return mOpacity; }

    public void setOpacity(double opacity) { mOpacity = opacity; }

    public Image getBackgroundImage() { return mBackgroundImage; }

    public void setBackgroundImage(Image image) { mBackgroundImage = image; }

    public Image.ContentMode getBackgroundContentMode() { return mBackgroundContentMode; }

    public void setBackgroundContentMode(Image.ContentMode mode) { mBackgroundContentMode = mode; }

    public double getBackgroundScale() { return mBackgroundScale; }

    public void setBackgroundScale(double scale) { mBackgroundScale = scale; }

    public CustomKeys getCustomKeys() { return mCustomKeys; }

    public void setCustomKeys(CustomKeys keys) { mCustomKeys = keys; }

    /*
        Parcelable
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
        mInset = (Inset) in.readValue(Inset.class.getClassLoader());
        mAction = (Action) in.readValue(Action.class.getClassLoader());
        mOpacity = in.readDouble();
        mBackgroundImage = (Image) in.readValue(Image.class.getClassLoader());
        mBackgroundScale = in.readDouble();
        mBackgroundContentMode = (Image.ContentMode) in.readSerializable();
        mCustomKeys = in.readParcelable(CustomKeys.class.getClassLoader());
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
        dest.writeValue(mInset);
        dest.writeValue(mAction);
        dest.writeDouble(mOpacity);
        dest.writeValue(mBackgroundImage);
        dest.writeDouble(mBackgroundScale);
        dest.writeSerializable(mBackgroundContentMode);
        dest.writeParcelable(mCustomKeys, 0);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Block> CREATOR = new Parcelable.Creator<Block>() {
        @Override
        public Block createFromParcel(Parcel in) {
            return new Block(in);
        }

        @Override
        public Block[] newArray(int size) {
            return new Block[size];
        }
    };
}
