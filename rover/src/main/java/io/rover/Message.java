package io.rover;

import java.net.URL;
import java.util.Date;

/**
 * Created by ata_n on 2016-04-05.
 */
public class Message {

    enum Action {
        None, Link
    }

    private String mId;
    private String mTitle;
    private String mText;
    private Date mTimestamp;
    private boolean mRead;
    private Action mAction;
    private URL mURL;

    public String getId() { return mId; }

    public Action getAction() { return mAction; }

    public void setAction(Action action) {
        mAction = action;
    }

    public URL getURL() { return mURL; }

    public void setURL(URL url) {
        mURL = url;
    }

    public boolean isRead() { return mRead; }

    public void setRead(boolean read) {
        mRead = read;
    }

    public Message(String title, String text, Date timestamp, String id) {
        mId = id;
        mTimestamp = timestamp;
        mText = text;
        mTitle = title;
    }

    public String getTitle() { return mTitle; }

    public String getText() { return mText; }

}
