package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-08-16.
 */
public class Inset implements Parcelable {
    public int top;
    public int right;
    public int bottom;
    public int left;

    public static Inset ZeroInset = new Inset(0,0,0,0);

    public Inset(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    protected Inset(Parcel in) {
        top = in.readInt();
        right = in.readInt();
        bottom = in.readInt();
        left = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(top);
        dest.writeInt(right);
        dest.writeInt(bottom);
        dest.writeInt(left);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Inset> CREATOR = new Parcelable.Creator<Inset>() {
        @Override
        public Inset createFromParcel(Parcel in) {
            return new Inset(in);
        }

        @Override
        public Inset[] newArray(int size) {
            return new Inset[size];
        }
    };
}
