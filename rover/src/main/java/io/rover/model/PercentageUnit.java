package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-06-16.
 */
public class PercentageUnit extends Unit {
    public PercentageUnit(Double value) {
        super(value);
    }

    /*
        Parcelable
     */

    protected PercentageUnit(Parcel in) {
        super(in);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Unit> CREATOR = new Parcelable.Creator<Unit>() {
        @Override
        public Unit createFromParcel(Parcel in) {
            return new PercentageUnit(in);
        }

        @Override
        public Unit[] newArray(int size) {
            return new PercentageUnit[size];
        }
    };
}
