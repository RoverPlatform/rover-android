package io.rover;

import com.google.android.gms.location.Geofence;

import java.util.List;

import io.rover.model.BeaconConfiguration;
import io.rover.model.Block;
import io.rover.model.Experience;
import io.rover.model.Message;
import io.rover.model.Place;
import io.rover.model.Screen;

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

    interface ExperienceObserver extends RoverObserver {
        void onExperienceLaunch(Experience experience, String sessionId);
        void onExperienceDismiss(Experience experience, String sessionId);
        void onScreenView(Screen screen, Experience experience, Screen fromScreen, Block fromBlock, String sessionId);
        void onBlockClick(Block block, Screen screen, Experience experience, String sessionId);
    }
}