package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-09-14.
 */
public class ExperienceDismissEvent extends Event {
    private Experience mExperience;

    public ExperienceDismissEvent(Experience experience, Date date) {
        mDate = date;
        mExperience = experience;
    }

    public Experience getExperience() { return mExperience; }
}
