package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-04-04.
 */
public class BeaconTransitionEvent extends Event {

    public static final int TRANSITION_ENTER = 0;
    public static final int TRANSITION_EXIT = 1;

    private int mTransition;
    private BeaconConfiguration mConfiguration;

    public BeaconTransitionEvent(int transition, String id, Date date) {
        mTransition = transition;
        mDate = date;
        mId = id;
    }

    public int getTransition() {
        return mTransition;
    }

    public BeaconConfiguration getBeaconConfiguration() {
        return mConfiguration;
    }

    public void setBeaconConfiguration(BeaconConfiguration configuration) {
        mConfiguration = configuration;
    }
}
