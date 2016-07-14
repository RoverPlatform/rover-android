package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-06-16.
 */
public abstract class Unit implements Parcelable {
    private double mValue;

    public Unit(double value) {
        mValue = value;
    }

    public double getValue() { return  mValue; }

    /** Parcelable
     */

    protected Unit(Parcel in) {
        mValue = in.readDouble();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(mValue);
    }
}
