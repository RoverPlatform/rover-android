package io.rover.model;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-09-14.
 */
public class ExperienceDismissEvent extends Event {
    private Experience mExperience;
    private String mSessionId;
    private String mCampaignId;

    public ExperienceDismissEvent(Experience experience, String sessionId, String campaignId, Date date) {
        mDate = date;
        mExperience = experience;
        mSessionId = sessionId;
        mCampaignId = campaignId;
    }

    public Experience getExperience() { return mExperience; }

    public String getSessionId() { return mSessionId; }

    public String getCampaignId() {
        return mCampaignId;
    }
}
