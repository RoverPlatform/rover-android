package io.rover.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

/**
 * Created by ata_n on 2016-07-07.
 */
public class ImageBlockView extends BlockView {

    private RemoteImageView mImageView;

    public ImageBlockView(Context context) {
        super(context);
        mImageView = new RemoteImageView(context);
        mImageView.setScaleType(ImageView.ScaleType.FIT_XY);
        //mImageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(mImageView);
    }

    public void setImageUrl(String url) {
        mImageView.setImage(url);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        mImageView.setLayoutParams(new LayoutParams(w, h));
    }

}
