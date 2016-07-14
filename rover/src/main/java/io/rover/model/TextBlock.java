package io.rover.model;

import android.graphics.Paint;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

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
    private Font mFont;
    

    public TextBlock() {
        super();
        mFont = new Font(12, 300);
    }

    public String getText() { return mText; }

    public void setText(String text) { mText = text; }

    public Alignment getTextAlignment() { return mTextAlignment; }

    public void setTextAlignment(Alignment alignment) { mTextAlignment = alignment; }

    public int getTextColor() { return mTextColor; }

    public void setTextColor(int color) { mTextColor = color; }

    public Offset getTextOffset() { return mTextOffset; }

    public void setTextOffset(Offset offset) { mTextOffset = offset; }

    public Font getFont() { return mFont; }

    public void setFont(Font font) { mFont = font; }

    /** Parcelable
     */

    protected TextBlock(Parcel in) {
        super(in);
        mText = in.readString();
        mTextAlignment = (Alignment) in.readValue(Alignment.class.getClassLoader());
        mTextColor = in.readInt();
        mTextOffset = (Offset) in.readValue(Offset.class.getClassLoader());
        mFont = (Font) in.readValue(Font.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mText);
        dest.writeValue(mTextAlignment);
        dest.writeInt(mTextColor);
        dest.writeValue(mTextOffset);
        dest.writeValue(mFont);
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
