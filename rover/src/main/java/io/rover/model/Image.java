package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by ata_n on 2016-06-29.
 */
public class Image implements Parcelable {

    public enum ContentMode {
        Original, Stretch, Tile, Fill, Fit
    }

    private double mWidth;
    private double mHeight;
    private String mUrl;

    public Image(double width, double height, String url) {
        mWidth = width;
        mHeight = height;
        mUrl = url;
    }

    public double getWidth() { return mWidth; }

    public double getHeight() { return mHeight; }

    public String getImageUrl() { return mUrl; }

    public double getAspectRatio() {
        if (mHeight != 0) {
            return mWidth / mHeight;
        }
        return 1;
    }

    /** Parcelable
     */

    protected Image(Parcel in) {
        mWidth = in.readDouble();
        mHeight = in.readDouble();
        mUrl = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(mWidth);
        dest.writeDouble(mHeight);
        dest.writeString(mUrl);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Image> CREATOR = new Parcelable.Creator<Image>() {
        @Override
        public Image createFromParcel(Parcel in) {
            return new Image(in);
        }

        @Override
        public Image[] newArray(int size) {
            return new Image[size];
        }
    };
}
