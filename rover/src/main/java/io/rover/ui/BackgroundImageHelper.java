package io.rover.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import io.rover.model.Image;

/**
 * Created by ata_n on 2016-09-02.
 */
public class BackgroundImageHelper {
    static void setBackgroundImage(ImageView imageView, Bitmap bitmap, float screenDensity, float backgroundScale, Image.ContentMode mode, Resources resources) {
        bitmap.setDensity((int)(bitmap.getDensity() /  (screenDensity / backgroundScale)) );

        switch (mode) {
            case Fit: {
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setImageBitmap(bitmap);
                break;
            }
            case Fill: {
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageBitmap(bitmap);
                break;
            }
            case Stretch: {
                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                imageView.setImageBitmap(bitmap);
                break;
            }
            case Tile: {
                imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                BitmapDrawable bitmapDrawable = new BitmapDrawable(resources, bitmap);
                bitmapDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                imageView.setImageDrawable(bitmapDrawable);
                break;
            }
            default: {
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setImageBitmap(bitmap);
                break;
            }
        }
    }
}
