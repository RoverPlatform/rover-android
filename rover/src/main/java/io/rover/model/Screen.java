package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Screen implements Parcelable {

    private String mTitle;
    private ArrayList<Row> mHeaderRows;
    private ArrayList<Row> mRows;
    private ArrayList<Row> mFooterRows;

    public Screen(ArrayList<Row> rows) {
        mRows = rows;
    }

    public String getTitle() { return mTitle; }

    public void setTitle(String title) { mTitle = title; }

    public ArrayList<Row> getRows() { return mRows; }

    public ArrayList<Row> getHeaderRows() { return mHeaderRows; }

    public ArrayList<Row> getFooterRows() { return mFooterRows; }

    public void setHeaderRows(ArrayList<Row> rows) { mHeaderRows = rows; }

    public void setFooterRows(ArrayList<Row> rows) { mFooterRows = rows; }

    /** Parcelable
     */

    protected Screen(Parcel in) {
        mTitle = in.readString();
        if (in.readByte() == 0x01) {
            mHeaderRows = new ArrayList<Row>();
            in.readList(mHeaderRows, Row.class.getClassLoader());
        } else {
            mHeaderRows = null;
        }
        if (in.readByte() == 0x01) {
            mRows = new ArrayList<Row>();
            in.readList(mRows, Row.class.getClassLoader());
        } else {
            mRows = null;
        }
        if (in.readByte() == 0x01) {
            mFooterRows = new ArrayList<Row>();
            in.readList(mFooterRows, Row.class.getClassLoader());
        } else {
            mFooterRows = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mTitle);
        if (mHeaderRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mHeaderRows);
        }
        if (mRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mRows);
        }
        if (mFooterRows == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mFooterRows);
        }
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Screen> CREATOR = new Parcelable.Creator<Screen>() {
        @Override
        public Screen createFromParcel(Parcel in) {
            return new Screen(in);
        }

        @Override
        public Screen[] newArray(int size) {
            return new Screen[size];
        }
    };
}
