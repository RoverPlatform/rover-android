package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by ata_n on 2016-06-16.
 */
public class TextBlock extends Block {

    /*
        var text: String?
    var textAlignment = Alignment(horizontal: .Left, vertical: .Top)
    var textColor = UIColor.blackColor()
    var textOffset = Offset.ZeroOffset
    var font = UIFont.systemFontOfSize(12)

     */

    private String mText;
    private Alignment mTextAlignment;
    private int mTextColor;
    private Offset mTextOffset;
    

    public TextBlock() {
        super();
    }

    /** Parcelable
     */

    protected TextBlock(Parcel in) {
        super(in);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Block> CREATOR = new Parcelable.Creator<Block>() {
        @Override
        public Block createFromParcel(Parcel in) {
            return new TextBlock(in);
        }

        @Override
        public Block[] newArray(int size) {
            return new TextBlock[size];
        }
    };
}
