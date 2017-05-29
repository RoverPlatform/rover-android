package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-07-08.
 */
public class ButtonBlock extends Block {

    public enum State {
        Normal, Highlighted, Selected, Disabled
    }

    private Appearance mNormalAppearance;
    private Appearance mHighlightedAppearance;
    private Appearance mSelectedAppearance;
    private Appearance mDisabledAppearance;

    public ButtonBlock() {
        super();
    }

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

    /*
        Parcelable
     */

    protected ButtonBlock(Parcel in) {
        super(in);
        mNormalAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mHighlightedAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mSelectedAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
        mDisabledAppearance = (Appearance) in.readValue(Appearance.class.getClassLoader());
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
