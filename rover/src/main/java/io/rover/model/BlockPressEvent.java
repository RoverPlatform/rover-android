package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-09-14.
 */
public class BlockPressEvent extends Event {
    private Block mBlock;
    private Screen mScreen;
    private Experience mExperience;

    public BlockPressEvent(Block block, Screen screen, Experience experience, Date date) {
        mBlock = block;
        mScreen = screen;
        mExperience = experience;
        mDate = date;
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
}
