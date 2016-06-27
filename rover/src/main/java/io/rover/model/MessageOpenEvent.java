package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-06-21.
 */
public class MessageOpenEvent extends Event {

    public enum Source {
        Notification, Message
    }

    public MessageOpenEvent(Message message, Source source, Date date) {
        mDate = date;
    }
}
