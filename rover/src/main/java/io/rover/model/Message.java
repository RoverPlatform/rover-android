package io.rover.model;

import android.net.Uri;
import android.renderscript.ScriptC;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Map;

/**
 * Created by ata_n on 2016-04-05.
 */
public class Message {

    public enum Action {
        None, Website, DeepLink, LandingPage, Experience
    }

    private String mId;
    private String mTitle;
    private String mText;
    private Date mTimestamp;
    private boolean mRead;
    private Action mAction;
    private URI mURI;
    private Screen mLandingPage;
    private Map<String, String> mProperties;
    private String mExperienceId;

    public String getId() { return mId; }

    public Action getAction() { return mAction; }

    public void setAction(Action action) {
        mAction = action;
    }

    public URI getURI() { return mURI; }

    public void setURI(URI uri) {
        mURI = uri;
    }

    public boolean isRead() { return mRead; }

    public void setRead(boolean read) {
        mRead = read;
    }

    public void setLandingPage(Screen screen) { mLandingPage = screen; }

    public Screen getLandingPage() { return mLandingPage; }

    public Date getTimestamp() { return mTimestamp; }

    public String getExperienceId() { return mExperienceId; }

    public void setExperienceId(String id) { mExperienceId = id; }

    public Uri getExperienceUri() {
        return new Uri.Builder().scheme("rover")
                .authority("experience")
                .appendPath(getExperienceId()).build();
    }

    public Message(String title, String text, Date timestamp, String id) {
        mId = id;
        mTimestamp = timestamp;
        mText = text;
        mTitle = title;
    }

    public String getTitle() { return mTitle; }

    public String getText() { return mText; }

    public Map<String, String> getProperties() { return mProperties; }

    public void setProperties(Map<String, String> properties) { mProperties = properties; }

}
