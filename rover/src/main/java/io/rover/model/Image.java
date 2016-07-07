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

    private double mWidth;
    private double mHeight;
    private URI mURI;

    public Image(double width, double height, URI uri) {
        mWidth = width;
        mHeight = height;
        mURI = uri;
    }

    public double getWidth() { return mWidth; }

    public double getHeight() { return mHeight; }

    public URI getImageURI() { return mURI; }

    public double getAspectRatio() {
        if (mHeight != 0) {
            return mWidth / mHeight;
        }
        return 0;
    }

    /** Parcelable
     */

    protected Image(Parcel in) {
        mWidth = in.readDouble();
        mHeight = in.readDouble();
        try {
            mURI = (URI) new URI(in.readString());
        } catch (URISyntaxException e) {
            Log.e("ImageBlock", "Bad URI in parcel");
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(mWidth);
        dest.writeDouble(mHeight);
        dest.writeString(mURI.toString());
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
