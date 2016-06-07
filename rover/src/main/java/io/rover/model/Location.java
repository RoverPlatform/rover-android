package io.rover.model;

import java.util.List;

/**
 * Created by ata_n on 2016-04-20.
 */
public class Location {

    private double mLatitude;
    private double mLongitude;
    private double mRadius;
    private String mName;
    private List<String> mTags;

    public Location(double lat, double lng, double radius, String name, List<String> tags) {
        mLatitude = lat;
        mLongitude = lng;
        mRadius = radius;
        mName = name;
        mTags = tags;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public double getRadius() {
        return mRadius;
    }

    public String getName() {
        return mName;
    }

    public List<String> getTags() {
        return mTags;
    }
}
