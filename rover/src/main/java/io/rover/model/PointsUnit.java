package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-06-16.
 */
public class PointsUnit extends Unit {
    public PointsUnit(Double value) {
        super(value);
    }

    public static PointsUnit ZeroUnit = new PointsUnit(0.0);

    /** Parcelable
     */

    protected PointsUnit(Parcel in) {
        super(in);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Unit> CREATOR = new Parcelable.Creator<Unit>() {
        @Override
        public Unit createFromParcel(Parcel in) {
            return new PointsUnit(in);
        }

        @Override
        public Unit[] newArray(int size) {
            return new PointsUnit[size];
        }
    };
}
