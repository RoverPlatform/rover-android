package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Rover Labs Inc on 2016-06-29.
 */
public class ImageBlock extends Block {

    private Image mImage;

    public ImageBlock() {
        super();
    }

    public Image getImage() { return mImage; }

    public void setImage(Image image) { mImage = image; }

    /*
        Parcelable
     */

    protected ImageBlock(Parcel in) {
        super(in);
        mImage = (Image) in.readValue(Image.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeValue(mImage);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Block> CREATOR = new Parcelable.Creator<Block>() {
        @Override
        public Block createFromParcel(Parcel in) {
            return new ImageBlock(in);
        }

        @Override
        public Block[] newArray(int size) {
            return new ImageBlock[size];
        }
    };



}
