package io.rover.model;

import java.util.ArrayList;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Row {

    private Unit mHeight;
    private ArrayList<Block> mBlocks;

    public Row(ArrayList<Block> blocks) {
        mBlocks = blocks;
    }

    public ArrayList<Block> getBlocks() { return mBlocks; }

    public Unit getHeight() { return mHeight; }

    public void setHeight(Unit height) { mHeight = height; }
}
