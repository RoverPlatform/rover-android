package io.rover;

import com.google.android.gms.location.Geofence;

import java.util.List;

import io.rover.model.BeaconConfiguration;
import io.rover.model.Location;
import io.rover.model.Message;

/**
 * Created by ata_n on 2016-04-07.
 */
public interface RoverObserver {

    interface GeofenceRegistrationObserver extends RoverObserver {
        void onRegisteredGeofences(List<Geofence> geofences);
    }

    interface GeofenceTransitionObserver extends RoverObserver {
        void onEnterGeofence(Location location);
        void onExitGeofence(Location location);
    }

    interface BeaconTransitionObserver extends RoverObserver {
        void onEnterBeaconRegion(BeaconConfiguration configuration);
        void onExitBeaconRegion(BeaconConfiguration configuration);
    }

    interface MessageDeliveryObserver extends RoverObserver {
        boolean shouldDeliverMessage(Message message);
        void onDeliveredMessage(Message message);
    }
}