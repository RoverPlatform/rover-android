package io.rover.model;

import com.google.android.gms.location.Geofence;

import java.util.Date;

/**
 * Created by ata_n on 2016-04-04.
 */
public class GeofenceTransitionEvent extends Event {

    private int mTransition;
    private String mGeofenceId;
    private Location mLocation;

    public GeofenceTransitionEvent(String geofenceId, int transition, Date date) {
        mDate = date;
        mTransition = transition;
        mGeofenceId = geofenceId;
    }

    public int getGeofenceTransition() {
        return mTransition;
    }

    public String getGeofenceId() {
        return mGeofenceId;
    }

    public Location getLocation() {
        return mLocation;
    }

    public void setLocation(Location location) {
        mLocation = location;
    }
}
