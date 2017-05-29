package io.rover.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Rover Labs Inc on 2016-06-16.
 */
public class TextBlock extends Block {

    private String mText;
    private Alignment mTextAlignment;
    private int mTextColor;
    private Offset mTextOffset;
    private Font mFont;
    private Spanned mSpannedText;
    

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

    public Spanned getSpannedText() {
        if (mSpannedText != null) {
            return mSpannedText;
        }

        Spanned text = Html.fromHtml(mText);
        if (text instanceof SpannableStringBuilder) {
            SpannableStringBuilder spannableStringBuilder = (SpannableStringBuilder) text;
            String plainString = spannableStringBuilder.toString();

            int lastCharPosition = plainString.length() - 1;
            char lastChar = plainString.charAt(lastCharPosition);
            if (lastChar == '\n') {
                spannableStringBuilder.replace(lastCharPosition -1, lastCharPosition + 1, "");
            }

            plainString = spannableStringBuilder.toString();

//            int indexOfDoubleNewLine = plainString.indexOf("\n\n\n");
//            while (indexOfDoubleNewLine != -1) {
//                spannableStringBuilder.replace(indexOfDoubleNewLine, indexOfDoubleNewLine + 3, "\n\n");
//                plainString = spannableStringBuilder.toString();
//                indexOfDoubleNewLine = plainString.indexOf("\n\n\n", indexOfDoubleNewLine + 1);
//            }


            Pattern pattern = Pattern.compile("\n\n+");
            Matcher matcher = pattern.matcher(plainString);

            int indexOffset = 0;

            while (matcher.find()) {
                int start = matcher.start() - indexOffset;
                int end = matcher.end() - indexOffset;
                int length = end - start - 1;

                String replacementString = new String(new char[length]).replace("\0", "\n");

                spannableStringBuilder.replace(start, end, replacementString);

                indexOffset++;
            }

        }

        mSpannedText = text;

        return mSpannedText;
    }

    /*
        Parcelable
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
