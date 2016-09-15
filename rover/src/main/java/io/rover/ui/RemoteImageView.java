package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

/**
 * Created by ata_n on 2016-07-07.
 */
public class RemoteImageView extends ImageView implements AssetManager.AssetManagerListener {

    public RemoteImageView(Context context) {
        super(context);
    }

    public void setImage(String url) {
        setImage(url, new ColorDrawable(Color.TRANSPARENT));
    }

    public void setImage(String url, Drawable placeholderDrawable) {
        final AssetManager manager = AssetManager.getSharedAssetManager(getContext());
        //manager.cancel(this);
        setImageDrawable(placeholderDrawable);
        if (url != null) {
            manager.fetchAsset(url, this); // TODO: add size preferences from imgix
        }
    }

    public void cancelCurrentImageLoad() {
        AssetManager.getSharedAssetManager(getContext()).cancelAsset(this);
    }

    @Override
    public void onAssetSuccess(Bitmap bitmap) {
        setImageBitmap(bitmap);
        requestLayout();
    }

    @Override
    public void onAssetFailure() {

    }
}
