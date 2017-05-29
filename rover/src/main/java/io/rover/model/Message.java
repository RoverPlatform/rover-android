package io.rover.model;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Rover Labs Inc on 2016-04-05.
 */
public class Message implements Parcelable {

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
        mProperties = new HashMap<>();
    }

    public Message(Parcel in) {
        mId = in.readString();
        mTitle = in.readString();
        mText = in.readString();
        mTimestamp = new Date(in.readLong());
        mRead = in.readInt() == 1;
        mAction = Action.valueOf(in.readString());

        if (in.readByte() == (byte)(0x01)) {
            try {
                mURI = new URI(in.readString());
            } catch (URISyntaxException e) {
                mURI = null;
            }
        }

        if (in.readByte() == (byte)(0x01)) {
            mLandingPage = (Screen) in.readParcelable(Screen.class.getClassLoader());
        }

        int propertiesSize = in.readInt();
        mProperties = new HashMap<>();

        for (int i = 0; i < propertiesSize; i++) {
            mProperties.put(in.readString(), in.readString());
        }

        if (in.readByte() == (byte)(0x01)) {
            mExperienceId = in.readString();
        }
    }

    public String getTitle() { return mTitle; }

    public String getText() { return mText; }

    public Map<String, String> getProperties() { return mProperties; }

    public void setProperties(Map<String, String> properties) { mProperties = properties; }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mTitle);
        dest.writeString(mText);
        dest.writeLong(mTimestamp.getTime());
        dest.writeInt(mRead ? 1 : 0);
        dest.writeString(mAction.name());

        if (mURI == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeString(mURI.toString());
        }


        if (mLandingPage == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeParcelable(mLandingPage, flags);
        }


        dest.writeInt(mProperties.size());
        for (Map.Entry<String, String> entry : mProperties.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }

        if (mExperienceId == null) {
            dest.writeByte((byte) (0x00));
        } else {
            dest.writeByte((byte) (0x01));
            dest.writeString(mExperienceId);
        }

    }

    public static final Parcelable.Creator<Message> CREATOR = new Parcelable.Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

}
