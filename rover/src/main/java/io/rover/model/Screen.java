package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Screen implements Parcelable {

    private String mId;
    private String mTitle;
    private ArrayList<Row> mHeaderRows;
    private ArrayList<Row> mRows;
    private ArrayList<Row> mFooterRows;
    private int mBackgroundColor;
    private int mTitleColor;
    private int mActionBarColor;
    private int mStatusBarColor;
    private int mActionItemColor;
    private boolean mLightStatusBar;
    private boolean mUseDefaultActionBarStyle;
    private Image mBackgroundImage;
    private double mBackgroundScale;
    private Image.ContentMode mBackgroundContentMode;


    public Screen(ArrayList<Row> rows) {
        mRows = rows;
    }

    public String getId() { return mId; }

    public void setId(String id) { mId = id; }

    public String getTitle() { return mTitle; }

    public void setTitle(String title) { mTitle = title; }

    public ArrayList<Row> getRows() { return mRows; }

    public ArrayList<Row> getHeaderRows() { return mHeaderRows; }

    public ArrayList<Row> getFooterRows() { return mFooterRows; }

    public void setHeaderRows(ArrayList<Row> rows) { mHeaderRows = rows; }

    public void setFooterRows(ArrayList<Row> rows) { mFooterRows = rows; }

    public int getBackgroundColor() { return mBackgroundColor; }

    public void setBackgroundColor(int color) { mBackgroundColor = color; }

    public int getTitleColor() { return mTitleColor; }

    public void setTitleColor(int color) { mTitleColor = color; }

    public int getActionBarColor() { return mActionBarColor; }

    public void setActionBarColor(int color) { mActionBarColor = color; }

    public int getActionItemColor() { return mActionItemColor; }

    public void setActionItemColor(int color) { mActionItemColor = color; }

    public int getStatusBarColor() { return mStatusBarColor; }

    public void setStatusBarColor(int color) { mStatusBarColor = color; }

    public boolean isStatusBarLight() { return mLightStatusBar; }

    public void setStatusBarLight(boolean isLight) { mLightStatusBar = isLight; }

    public boolean useDefaultActionBarStyle() { return mUseDefaultActionBarStyle; }

    public void setUseDefaultActionBarStyle(boolean use) { mUseDefaultActionBarStyle = use; }

    public Image getBackgroundImage() {return mBackgroundImage;}

    public void setBackgroundImage(Image backgroundImage) { mBackgroundImage = backgroundImage;}

    public Image.ContentMode getBackgroundContentMode() { return mBackgroundContentMode; }

    public void setBackgroundContentMode(Image.ContentMode mode) { mBackgroundContentMode = mode; }

    public double getBackgroundScale() { return mBackgroundScale; }

    public void setBackgroundScale(double scale) { mBackgroundScale = scale; }

    /** Parcelable
     */

    protected Screen(Parcel in) {
        mId = in.readString();
        mTitle = in.readString();
        if (in.readByte() == 0x01) {
            mHeaderRows = new ArrayList<Row>();
            in.readList(mHeaderRows, Row.class.getClassLoader());
        } else {
            mHeaderRows = null;
        }
        if (in.readByte() == 0x01) {
            mRows = new ArrayList<Row>();
            in.readList(mRows, Row.class.getClassLoader());
        } else {
            mRows = null;
        }
        if (in.readByte() == 0x01) {
            mFooterRows = new ArrayList<Row>();
            in.readList(mFooterRows, Row.class.getClassLoader());
        } else {
            mFooterRows = null;
        }
        mBackgroundColor = in.readInt();
        mTitleColor = in.readInt();
        mActionBarColor = in.readInt();
        mStatusBarColor = in.readInt();
        mActionItemColor = in.readInt();
        mLightStatusBar = in.readByte() != 0x00;
        mUseDefaultActionBarStyle = in.readByte() != 0x00;
        mBackgroundImage = (Image) in.readValue(Image.class.getClassLoader());
        mBackgroundScale = in.readDouble();
        mBackgroundContentMode = (Image.ContentMode) in.readSerializable();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mTitle);
        if (mHeaderRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mHeaderRows);
        }
        if (mRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mRows);
        }
        if (mFooterRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mFooterRows);
        }
        dest.writeInt(mBackgroundColor);
        dest.writeInt(mTitleColor);
        dest.writeInt(mActionBarColor);
        dest.writeInt(mStatusBarColor);
        dest.writeInt(mActionItemColor);
        dest.writeByte((byte) (mLightStatusBar ? 0x01 : 0x00));
        dest.writeByte((byte) (mUseDefaultActionBarStyle ? 0x01 : 0x00));
        dest.writeValue(mBackgroundImage);
        dest.writeDouble(mBackgroundScale);
        dest.writeSerializable(mBackgroundContentMode);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Screen> CREATOR = new Parcelable.Creator<Screen>() {
        @Override
        public Screen createFromParcel(Parcel in) {
            return new Screen(in);
        }

        @Override
        public Screen[] newArray(int size) {
            return new Screen[size];
        }
    };
}
