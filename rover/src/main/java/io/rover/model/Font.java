package io.rover.model;

import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.renderscript.Type;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class Font implements Parcelable {
    private float mSize;
    private int mWeight;

    public Font(float size, int weight) {
        mSize = size;
        mWeight = weight;
    }

    public float getSize() { return mSize; }

    public Typeface getTypeface() {
        switch (mWeight) {
            case 100: return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case 200: return Typeface.create("sans-serif-thin", Typeface.NORMAL);
            case 300: return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case 400: return Typeface.create("sans-serif", Typeface.NORMAL);
            case 500: return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case 600: return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case 700: return Typeface.create("sans-serif", Typeface.BOLD);
            case 800: return Typeface.create("sans-serif", Typeface.BOLD);
            case 900: return Typeface.create("sans-serif-black", Typeface.NORMAL);
            default: return Typeface.create("sans-serif", Typeface.NORMAL);
        }
    }


    /*
        Parcelable
     */

    protected Font(Parcel in) {
        mSize = in.readFloat();
        mWeight = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mSize);
        dest.writeInt(mWeight);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Font> CREATOR = new Parcelable.Creator<Font>() {
        @Override
        public Font createFromParcel(Parcel in) {
            return new Font(in);
        }

        @Override
        public Font[] newArray(int size) {
            return new Font[size];
        }
    };
}
