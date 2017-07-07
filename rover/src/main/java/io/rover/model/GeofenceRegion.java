package io.rover.model;

/**
 * Created by Rover Labs Inc. on 2017-07-07.
 */

public class GeofenceRegion {

    private String mId;
    private double mLatitude;
    private double mLongitude;
    private int mRadius;



    public GeofenceRegion(String id, double latitude, double longitude, int radius) {
        mId = id;
        mLatitude = latitude;
        mLongitude = longitude;
        mRadius = radius;
    }

    public String getId() {
        return mId;
    }

    public double getlatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }

    public int getRadius() {
        return mRadius;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setLatitude(double latitude) {
        mLatitude = latitude;
    }

    public void setLongitude(double longitude) {
        mLongitude = longitude;
    }

    public void setRadius(int radius) {
        mRadius = radius;
    }
}
