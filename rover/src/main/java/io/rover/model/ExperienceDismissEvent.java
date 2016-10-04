package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-09-14.
 */
public class ExperienceDismissEvent extends Event {
    private Experience mExperience;
    private String mSessionId;

    public ExperienceDismissEvent(Experience experience, String sessionId, Date date) {
        mDate = date;
        mExperience = experience;
        mSessionId = sessionId;
    }

    public Experience getExperience() { return mExperience; }

    public String getSessionId() { return mSessionId; }
}
