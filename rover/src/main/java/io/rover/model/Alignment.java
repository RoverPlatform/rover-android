package io.rover.model;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Alignment {

    public enum Horizontal {
        Left, Center, Right, Fill
    }

    public enum Vertical {
        Top, Middle, Bottom, Fill
    }

    private Horizontal mHorizontal;
    private Vertical mVertical;

    public Alignment(Horizontal horizontal, Vertical vertical) {
        mHorizontal = horizontal;
        mVertical = vertical;
    }

    public Horizontal getHorizontal() { return mHorizontal; }

    public Vertical getVertical() { return mVertical; }
}
