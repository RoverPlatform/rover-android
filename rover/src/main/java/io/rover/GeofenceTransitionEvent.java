package io.rover;

import com.google.android.gms.location.Geofence;

import java.util.Date;

/**
 * Created by ata_n on 2016-04-04.
 */
public class GeofenceTransitionEvent extends Event {

    private int mTransition;

    public GeofenceTransitionEvent(String id, int transition, Date date) {
        mDate = date;
        mTransition = transition;
        mId = id;
    }

    public int getGeofenceTransition() { return mTransition; }
}
