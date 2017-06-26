package io.rover.model;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-09-14.
 */
public class ScreenViewEvent extends Event {
    private Screen mScreen;
    private Experience mExperience;
    private Screen mFromScreen;
    private Block mFromBlock;
    private String mSessionId;
    private String mCampaignId;

    public ScreenViewEvent(Screen screen, Experience experience, Screen fromScreen, Block fromBlock, String session, String campaignId, Date date) {
        mScreen = screen;
        mExperience = experience;
        mFromScreen = fromScreen;
        mFromBlock = fromBlock;
        mDate = date;
        mSessionId = session;
        mCampaignId = campaignId;
    }

    public Screen getScreen() {
        return mScreen;
    }

    public Experience getExperience() {
        return mExperience;
    }

    public Screen getFromScreen() {
        return mFromScreen;
    }

    public Block getFromBlock() {
        return mFromBlock;
    }

    public String getSessionId() { return mSessionId; }

    public String getCampaignId() {
        return mCampaignId;
    }
}
