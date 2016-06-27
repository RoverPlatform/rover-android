package io.rover.model;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Offset {

    private Unit mLeft, mTop, mRight, mBottom, mCenter, mMiddle;

    public Offset(Unit top, Unit right, Unit bottom, Unit left, Unit center, Unit middle) {
        mTop = top;
        mRight = right;
        mBottom = bottom;
        mLeft = left;
        mCenter = center;
        mMiddle = middle;
    }
}
