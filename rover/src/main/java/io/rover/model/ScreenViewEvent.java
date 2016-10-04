package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-09-14.
 */
public class ScreenViewEvent extends Event {
    private Screen mScreen;
    private Experience mExperience;
    private Screen mFromScreen;
    private Block mFromBlock;
    private String mSessionId;

    public ScreenViewEvent(Screen screen, Experience experience, Screen fromScreen, Block fromBlock, String session, Date date) {
        mScreen = screen;
        mExperience = experience;
        mFromScreen = fromScreen;
        mFromBlock = fromBlock;
        mDate = date;
        mSessionId = session;
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
}
