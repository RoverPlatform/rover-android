package io.rover;

import android.support.v4.app.Fragment;

import com.google.android.gms.location.Geofence;

import java.util.List;

import io.rover.model.BeaconConfiguration;
import io.rover.model.Block;
import io.rover.model.Experience;
import io.rover.model.Message;
import io.rover.model.Place;
import io.rover.model.Screen;

/**
 * Created by Rover Labs Inc on 2016-04-07.
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

    interface NotificationInteractionObserver extends RoverObserver {
        void onNotificationOpened(Message message);
        void onNotificationDeleted(Message message);
    }

    interface ExperienceObserver extends RoverObserver {
        void onExperienceLaunch(Experience experience, String sessionId);
        void onExperienceDismiss(Experience experience, String sessionId);
        void onScreenView(Screen screen, Experience experience, Screen fromScreen, Block fromBlock, String sessionId);
        void onBlockClick(Block block, Screen screen, Experience experience, String sessionId);
    }

    interface ExtendedExperienceObserver extends RoverObserver {
        void onExperienceLaunch(ExperienceActivity activity, Experience experience, String sessionId);
        void onExperienceDismiss(ExperienceActivity activity, Experience experience, String sessionId);

        void onScreenView(ExperienceActivity activity, Fragment screenFragment, Experience experience, Screen screen, Screen fromScreen, Block fromBlock, String sessionId);
        void onBlockClick(ExperienceActivity activity, Fragment screenFragment, Screen screen, Block block, String sessionId);
        /*
            The ExperienceActivity is about to present the next screen
            This gives you the option to return your own Fragment or let it continue
         */
        Fragment willPresentScreen(ExperienceActivity activity, Fragment screenFragment, Screen screen);
    }
}