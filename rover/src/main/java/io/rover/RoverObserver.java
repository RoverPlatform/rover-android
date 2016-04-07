package io.rover;

import com.google.android.gms.location.Geofence;

import java.util.List;

/**
 * Created by ata_n on 2016-04-07.
 */
public interface RoverObserver {

    void onRegisteredGeofences(List<Geofence> geofences);

    void onEnteredGeofence();
    void onExitedGeofence();

    boolean shouldDeliverMessage(Message message);
    void onDeliveredMessage(Message message);
}
