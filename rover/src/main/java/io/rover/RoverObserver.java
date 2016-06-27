package io.rover;

import com.google.android.gms.location.Geofence;

import java.util.List;

import io.rover.model.BeaconConfiguration;
import io.rover.model.Message;
import io.rover.model.Place;

/**
 * Created by ata_n on 2016-04-07.
 */
public interface RoverObserver {

    interface GeofenceRegistrationObserver extends RoverObserver {
        void onRegisteredGeofences(List<Geofence> geofences);
    }

    interface GeofenceTransitionObserver extends RoverObserver {
        void onEnterGeofence(Place place);
        void onExitGeofence(Place place);
    }

    interface BeaconTransitionObserver extends RoverObserver {
        void onEnterBeaconRegion(BeaconConfiguration configuration);
        void onExitBeaconRegion(BeaconConfiguration configuration);
    }

    interface MessageDeliveryObserver extends RoverObserver {
        void onMessageReceived(Message message);
    }
}