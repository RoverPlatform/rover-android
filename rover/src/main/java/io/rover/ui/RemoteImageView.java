package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.widget.ImageView;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class RemoteImageView extends ImageView implements AssetManager.AssetManagerListener {

    public RemoteImageView(@NonNull Context context) {
        super(context);
    }

    public void setImage(String url) {
        setImage(url, new ColorDrawable(Color.TRANSPARENT));
    }

    public void setImage(String url, Drawable placeholderDrawable) {

        //manager.cancel(this);
        setImageDrawable(placeholderDrawable);

        final AssetManager manager = AssetManager.getSharedAssetManager(getContext());

        if (manager != null && url != null) {
            manager.fetchAsset(url, this); // TODO: add size preferences from imgix
        }
    }

    public void cancelCurrentImageLoad() {
        AssetManager manager = AssetManager.getSharedAssetManager(getContext());
        if (manager != null)
            manager.cancelAsset(this);
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
