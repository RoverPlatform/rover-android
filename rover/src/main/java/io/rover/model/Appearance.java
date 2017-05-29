package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-07-08.
 */
public class Appearance implements Parcelable {

    public int titleColor;
    public String title;
    public Alignment titleAlignment;
    public Offset titleOffset;
    public Font titleFont;

    public int backgroundColor;
    public int borderColor;
    public double borderRadius;
    public double borderWidth;

    public Appearance() {

    }

    /*
        Parcelable
     */

    protected Appearance(Parcel in) {
        titleColor = in.readInt();
        title = in.readString();
        titleAlignment = (Alignment) in.readValue(Alignment.class.getClassLoader());
        titleOffset = (Offset) in.readValue(Offset.class.getClassLoader());
        titleFont = (Font) in.readValue(Font.class.getClassLoader());
        backgroundColor = in.readInt();
        borderColor = in.readInt();
        borderRadius = in.readDouble();
        borderWidth = in.readDouble();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(titleColor);
        dest.writeString(title);
        dest.writeValue(titleAlignment);
        dest.writeValue(titleOffset);
        dest.writeValue(titleFont);
        dest.writeInt(backgroundColor);
        dest.writeInt(borderColor);
        dest.writeDouble(borderRadius);
        dest.writeDouble(borderWidth);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Appearance> CREATOR = new Parcelable.Creator<Appearance>() {
        @Override
        public Appearance createFromParcel(Parcel in) {
            return new Appearance(in);
        }

        @Override
        public Appearance[] newArray(int size) {
            return new Appearance[size];
        }
    };
}
