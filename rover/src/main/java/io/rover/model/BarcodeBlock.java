package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc. on 2017-03-02.
 */

public class BarcodeBlock extends ImageBlock {

    private String mBarcodeText;
    private String mBarcodeType;

    public BarcodeBlock() {
        super();
    }

    public String getBarcodeText() {
        return mBarcodeText;
    }

    public void setBarcodeText(String barcodeText) {
        mBarcodeText = barcodeText;
    }

    public String getBarcodeType() {
        return mBarcodeType;
    }

    public void setBarcodeType(String barcodeType) {
        mBarcodeType = barcodeType;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mBarcodeText == null ? "" : mBarcodeText);
        dest.writeString(mBarcodeType == null ? "" : mBarcodeType);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Block> CREATOR = new Parcelable.Creator<Block>() {
        @Override
        public Block createFromParcel(Parcel in) {
            return new BarcodeBlock(in);
        }

        @Override
        public Block[] newArray(int size) {
            return new BarcodeBlock[size];
        }
    };

    private BarcodeBlock(Parcel in) {
        super(in);
        mBarcodeText = in.readString();
        mBarcodeType = in.readString();
    }
}
