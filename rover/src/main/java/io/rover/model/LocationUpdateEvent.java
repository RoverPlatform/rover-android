package io.rover.model;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-03-23.
 */
public class LocationUpdateEvent extends Event {

    private Location mLocation;

    public LocationUpdateEvent(Location location, Date date) {
        mLocation = location;
        mDate = date;
    }

    public Location getLocation() { return mLocation; }


    /*
        Parcelable
     */


    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mLocation, flags);
        dest.writeSerializable(mDate);
    }

    public static final Parcelable.Creator<LocationUpdateEvent> CREATOR = new Parcelable.Creator<LocationUpdateEvent>() {

        public LocationUpdateEvent createFromParcel(Parcel in) {
            return new LocationUpdateEvent(in);
        }

        public LocationUpdateEvent[] newArray(int size) {
            return new LocationUpdateEvent[size];
        }
    };

    private LocationUpdateEvent(Parcel in) {
        mLocation = in.readParcelable(Location.class.getClassLoader());
        mDate = (Date)in.readSerializable();
    }
}
