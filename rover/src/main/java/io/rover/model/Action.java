package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-07-08.
 */
public class Action implements Parcelable {

    public final static String WEBSITE_ACTION = "website-action";
    public final static String DEEPLINK_ACTION = "deep-link-action";
    public final static String GOTO_SCREEN_ACTION = "go-to-screen";
    public final static String OPEN_URL_ACTION = "open-url-action";

    private String mType;
    private String mUrl;

    public Action(String type, String url) {
        mType = type;
        mUrl = url;
    }

    public String getType() { return mType; }

    public String getUrl() { return mUrl; }

    /** Parcelable
     */

    protected Action(Parcel in) {
        mType = in.readString();
        mUrl = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeString(mUrl);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Action> CREATOR = new Parcelable.Creator<Action>() {
        @Override
        public Action createFromParcel(Parcel in) {
            return new Action(in);
        }

        @Override
        public Action[] newArray(int size) {
            return new Action[size];
        }
    };
}
