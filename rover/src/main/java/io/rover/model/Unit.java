package io.rover.model;

/**
 * Created by ata_n on 2016-06-16.
 */
public abstract class Unit {
    private double mValue;

    public Unit(double value) {
        mValue = value;
    }

    public double getValue() { return  mValue; }
}
