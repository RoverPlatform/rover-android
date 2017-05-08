package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Rover Labs Inc. on 2017-05-08.
 */

public class CustomKeys extends HashMap<String,String> implements Parcelable {

    private HashMap<String,String> mKeys;

    public CustomKeys(Parcel in) {
        super();

        int size = in.readInt();

        for (int i = 0; i < size; i++) {
            String key = in.readString();
            String value = in.readString();
            put(key, value);
        }
    }

    public CustomKeys(int capacity) {
        super(capacity);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mKeys.size());
        for (Map.Entry<String, String> entry : mKeys.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }

    public static final Parcelable.Creator<CustomKeys> CREATOR = new Parcelable.Creator<CustomKeys>() {
        @Override
        public CustomKeys createFromParcel(Parcel in) {
            return new CustomKeys(in);
        }

        @Override
        public CustomKeys[] newArray(int size) {
            return new CustomKeys[size];
        }
    };
}
