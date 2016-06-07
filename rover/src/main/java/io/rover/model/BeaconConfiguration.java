package io.rover.model;

/**
 * Created by ata_n on 2016-04-20.
 */
public class BeaconConfiguration {

    //private UID
    private String mName;
    private String[] mTags;

    public BeaconConfiguration(String name, String[] tags) {
        mName = name;
        mTags = tags;
    }

    public String getName() {
        return mName;
    }

    public String[] getTags() {
        return mTags;
    }
}
