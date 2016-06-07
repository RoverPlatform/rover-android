package io.rover.model;

import android.os.Parcelable;

import java.util.Date;

import dalvik.annotation.TestTargetClass;

/**
 * Created by ata_n on 2016-03-23.
 */
public abstract class Event /*implements Parcelable */{
    Date mDate;
    String mId;

    public Date getDate() { return mDate; }
    public String getId() { return mId; }
}
