package io.rover.model;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Map;

/**
 * Created by ata_n on 2016-07-08.
 */
public class ButtonBlock extends Block {

    public enum State {
        Normal, Highlighted, Selected, Disabled
    }

    private Appearance mNormalAppearance;
    private Appearance mHighlightedAppearance;
    private Appearance mSelectedAppearance;
    private Appearance mDisabledAppearance;
    private Action mAction;

    public ButtonBlock() {
        super();
    }

    public Action getAction() { return mAction; }

    public void setAction(Action action) { mAction = action; }

    public void setAppearance(Appearance appearance, State state) {
        switch (state) {
            case Normal:{
                mNormalAppearance = appearance;
                break;
            }
            case Highlighted:{
                mHighlightedAppearance = appearance;
                break;
            }
            case Selected:{
                mSelectedAppearance = appearance;
                break;
            }
            case Disabled:{
                mDisabledAppearance = appearance;
                break;
            }
        }
    }

    public Appearance getAppearance(State state) {
        switch (state) {
            case Normal: return mNormalAppearance;
            case Highlighted: return mHighlightedAppearance;
            case Selected: return mSelectedAppearance;
            case Disabled: return mDisabledAppearance;
            default: return mNormalAppearance;
        }
    }

    // TODO: root level appearance getters should return default neutral values

    /** Parcelable
     */

    protected ButtonBlock(Parcel in) {
        super(in);
        mNormalAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mHighlightedAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mSelectedAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mDisabledAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mAction = (Action) in.readValue(Action.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeValue(mNormalAppearance);
        dest.writeValue(mHighlightedAppearance);
        dest.writeValue(mSelectedAppearance);
        dest.writeValue(mDisabledAppearance);
        dest.writeValue(mAction);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<ButtonBlock> CREATOR = new Parcelable.Creator<ButtonBlock>() {
        @Override
        public ButtonBlock createFromParcel(Parcel in) {
            return new ButtonBlock(in);
        }

        @Override
        public ButtonBlock[] newArray(int size) {
            return new ButtonBlock[size];
        }
    };
}
