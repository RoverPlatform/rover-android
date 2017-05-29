package io.rover.model;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-09-14.
 */
public class ExperienceLaunchEvent extends Event {
    private Experience mExperience;
    private String mSessionId;

    public ExperienceLaunchEvent(Experience experience, String session, Date date) {
        mDate = date;
        mExperience = experience;
        mSessionId = session;
    }

    public Experience getExperience() {
        return mExperience;
    }

    public String getSessionId() {
        return mSessionId;
    }
}
