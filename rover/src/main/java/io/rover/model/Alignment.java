package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-06-16.
 */
public class Alignment implements Parcelable {

    public enum Horizontal {
        Left, Center, Right, Fill
    }

    public enum Vertical {
        Top, Middle, Bottom, Fill
    }

    private Horizontal mHorizontal;
    private Vertical mVertical;

    public Alignment(Horizontal horizontal, Vertical vertical) {
        mHorizontal = horizontal;
        mVertical = vertical;
    }

    public Horizontal getHorizontal() { return mHorizontal; }

    public Vertical getVertical() { return mVertical; }

    /*
        Parcelable
     */

    protected Alignment(Parcel in) {
        mHorizontal = (Horizontal) in.readSerializable();
        mVertical = (Vertical) in.readSerializable();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mHorizontal);
        dest.writeSerializable(mVertical);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Alignment> CREATOR = new Parcelable.Creator<Alignment>() {
        @Override
        public Alignment createFromParcel(Parcel in) {
            return new Alignment(in);
        }

        @Override
        public Alignment[] newArray(int size) {
            return new Alignment[size];
        }
    };
}
