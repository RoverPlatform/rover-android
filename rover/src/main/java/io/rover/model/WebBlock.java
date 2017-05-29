package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-09-09.
 */
public class WebBlock extends Block {
    private String mURL;
    private boolean mScrollable;

    public WebBlock() {
        mScrollable = false;
    }

    public void setScrollable(boolean scrollable) {
        mScrollable = scrollable;
    }

    public boolean isScrollable() { return mScrollable; }

    public String getURL() { return mURL; }

    public void setURL(String url) { mURL = url; }

    /*
        Parcelable
     */

    protected WebBlock(Parcel in) {
        super(in);
        mURL = in.readString();
        mScrollable = in.readByte() != 0x00;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mURL);
        dest.writeByte((byte) (mScrollable ? 0x01 : 0x00));
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Block> CREATOR = new Parcelable.Creator<Block>() {
        @Override
        public Block createFromParcel(Parcel in) {
            return new WebBlock(in);
        }

        @Override
        public Block[] newArray(int size) {
            return new WebBlock[size];
        }
    };
}
