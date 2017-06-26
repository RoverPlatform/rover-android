package io.rover.model;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-09-14.
 */
public class BlockPressEvent extends Event {
    private Block mBlock;
    private Screen mScreen;
    private Experience mExperience;
    private String mSessionId;
    private String mCampaignId;

    public BlockPressEvent(Block block, Screen screen, Experience experience, String session, String campaignId, Date date) {
        mBlock = block;
        mScreen = screen;
        mExperience = experience;
        mDate = date;
        mSessionId = session;
        mCampaignId = campaignId;
    }

    public Block getBlock() {
        return mBlock;
    }

    public Screen getScreen() {
        return mScreen;
    }

    public Experience getExperience() {
        return mExperience;
    }

    public String getSessionId() { return mSessionId; }

    public String getCampaignId() {
        return mCampaignId;
    }
}
