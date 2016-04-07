package io.rover;

import java.util.Date;

/**
 * Created by ata_n on 2016-04-04.
 */
public class BeaconTransitionEvent extends Event {

    public static int TRANSITION_ENTER = 0;
    public static int TRANSITION_EXIT = 1;

    private int mTransition;

    public BeaconTransitionEvent(int transition, String id, Date date) {
        mTransition = transition;
        mDate = date;
        mId = id;
    }

    public int getTransition() { return mTransition; }
}
