package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.widget.ImageView;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class ImageBlockView extends BlockView {

    private RemoteImageView mImageView;

    public ImageBlockView(@NonNull Context context) {
        super(context);
        mImageView = new RemoteImageView(context);
        mImageView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(mImageView);
        //mImageView.setWillNotDraw(true);
    }

    public void setImageBitmap(Bitmap bitmap) {
        mImageView.setImageBitmap(bitmap);
    }

    public void clearImage() {
        mImageView.setImageDrawable(null);
    }

    public void setImageUrl(String url) {
        mImageView.setImage(url);
    }

    public void cancelDownload() {
        mImageView.cancelCurrentImageLoad();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mImageView.setLayoutParams(new LayoutParams(w, h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //mImageView.draw(canvas);
    }
}
