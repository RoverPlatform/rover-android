package io.rover.model;

/**
 * Created by ata_n on 2016-06-16.
 */
public abstract class Block {
    /*
        // Layout


    // Appearance

    var backgroundColor = UIColor.clearColor()
    var borderColor = UIColor.clearColor()
    var borderRadius: CGFloat = 0
    var borderWidth: CGFloat = 0
     */

    // Layout

    public enum Position {
        Stacked, Floating
    }

    private Position mPosition;

    private Unit mHeight;
    private Unit mWidth;

    private Alignment mAlignment;
    private Offset mOffset;

    public Position getPosition() { return mPosition; }

    public void setPosition(Position position) { mPosition = position; }

    public Unit getHeight() { return mHeight; }

    public void setHeight(Unit height) { mHeight = height; }

    public Unit getWidth() { return mWidth; }

    public void setWidth(Unit width) { mWidth = width; }

    public Alignment getAlignment() { return mAlignment; }

    public void setAlignment(Alignment alignment) { mAlignment = alignment; }

    public Offset getOffset() { return mOffset; }

    public void setOffset(Offset offset) { mOffset = offset; }

    // TODO: Appearance
}
