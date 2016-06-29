package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Offset implements Parcelable {

    private Unit mLeft;
    private Unit mTop;
    private Unit mRight;
    private Unit mBottom;
    private Unit mCenter;
    private Unit mMiddle;

    public Offset(Unit top, Unit right, Unit bottom, Unit left, Unit center, Unit middle) {
        mTop = top;
        mRight = right;
        mBottom = bottom;
        mLeft = left;
        mCenter = center;
        mMiddle = middle;
    }

    public Unit getTop() { return mTop; }
    public Unit getRight() { return mRight; }
    public Unit getBottom() { return mBottom; }
    public Unit getLeft() { return mLeft; }
    public Unit getCenter() { return mCenter; }
    public Unit getMiddle() { return mMiddle; }

    /** Parcelable
     */

    protected Offset(Parcel in) {
        mLeft = (Unit) in.readValue(Unit.class.getClassLoader());
        mTop = (Unit) in.readValue(Unit.class.getClassLoader());
        mRight = (Unit) in.readValue(Unit.class.getClassLoader());
        mBottom = (Unit) in.readValue(Unit.class.getClassLoader());
        mCenter = (Unit) in.readValue(Unit.class.getClassLoader());
        mMiddle = (Unit) in.readValue(Unit.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mLeft);
        dest.writeValue(mTop);
        dest.writeValue(mRight);
        dest.writeValue(mBottom);
        dest.writeValue(mCenter);
        dest.writeValue(mMiddle);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Offset> CREATOR = new Parcelable.Creator<Offset>() {
        @Override
        public Offset createFromParcel(Parcel in) {
            return new Offset(in);
        }

        @Override
        public Offset[] newArray(int size) {
            return new Offset[size];
        }
    };
}
