package io.rover.model;

import java.util.Date;

/**
 * Created by Rover Labs Inc on 2016-03-23.
 */
public abstract class Event {
    Date mDate;
    String mId;

    public Date getDate() { return mDate; }
    public String getId() { return mId; }
}
