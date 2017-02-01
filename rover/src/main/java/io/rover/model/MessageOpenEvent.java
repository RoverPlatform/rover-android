package io.rover.model;

import java.util.Date;

/**
 * Created by ata_n on 2016-06-21.
 */
public class MessageOpenEvent extends Event {

    public enum Source {
        Inbox, Notification
    }

    private Message mMessage;
    private Source mSource;

    public MessageOpenEvent(Message message, Source source, Date date) {
        mMessage = message;
        mSource = source;
        mDate = date;
    }

    public Message getMessage() {
        return mMessage;
    }

    public Source getSource() {
        return mSource;
    }
}
