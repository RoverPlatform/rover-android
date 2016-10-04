package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ata_n on 2016-08-16.
 */
public class Experience implements Parcelable {
    private List<Screen> mScreens;
    private String mHomeScreenId;
    private String mId;
    private String mVersion;

    public Experience(List<Screen> screens, String homeScreenId, String id) {
        mScreens = screens;
        mHomeScreenId = homeScreenId;
        mId = id;
    }

    public String getId() { return mId; }

    public List<Screen> getScreens() {
        return mScreens;
    }

    public Screen getHomeScreen() {
        return getScreen(mHomeScreenId);
    }

    public Screen getScreen(String id) {
        if (id == null) {
            return null;
        }

        for (Screen screen :
                mScreens) {
            if (screen.getId().equals(id)) {
                return screen;
            }
        }
        return null;
    }

    public String getVersion() { return mVersion; }

    public void setVersion(String version) { mVersion = version; }


    protected Experience(Parcel in) {
        if (in.readByte() == 0x01) {
            mScreens = new ArrayList<>();
            in.readList(mScreens, Screen.class.getClassLoader());
        } else {
            mScreens = null;
        }
        mHomeScreenId = in.readString();
        mVersion = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mScreens == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeList(mScreens);
        }
        dest.writeString(mHomeScreenId);
        dest.writeString(mVersion);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Experience> CREATOR = new Parcelable.Creator<Experience>() {
        @Override
        public Experience createFromParcel(Parcel in) {
            return new Experience(in);
        }

        @Override
        public Experience[] newArray(int size) {
            return new Experience[size];
        }
    };
}
