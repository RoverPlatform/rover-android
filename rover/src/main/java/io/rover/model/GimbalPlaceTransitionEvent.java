package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-07-28.
 */
public class GimbalPlaceTransitionEvent extends Event {

    public static final int TRANSITION_ENTER = 0;
    public static final int TRANSITION_EXIT = 1;

    private int mTransition;
    private String mPlaceId;

    public GimbalPlaceTransitionEvent(String placeId, int transition, Date date) {
        mDate = date;
        mTransition = transition;
        mPlaceId = placeId;
    }

    public int getGimbalPlaceTransition() {
        return mTransition;
    }

    public String getPlaceId() {
        return mPlaceId;
    }
}
