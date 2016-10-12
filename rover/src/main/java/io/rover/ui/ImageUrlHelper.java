package io.rover.ui;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import io.rover.model.Image;

/**
 * Created by ata_n on 2016-10-12.
 */
public class ImageUrlHelper {
    public static String getOptimizedImageUrl(int blockWidth, int blockHeight, @NonNull Image image, Image.ContentMode mode, Double scale) {

        Log.i("OPTIMIZEDIMAGE", image.getImageUrl());
        Log.i("OPTIMIZEDIMAGE", Integer.toString(blockWidth));
        Log.i("OPTIMIZEDIMAGE", Integer.toString(blockHeight));
        if ((image.getWidth() < blockWidth) || blockWidth == 0 || blockHeight == 0) {
            // Don't optimize that is already smaller
            return image.getImageUrl();
        }

        Uri.Builder builder = Uri.parse(image.getImageUrl()).buildUpon();
        if (mode == Image.ContentMode.Original || mode == Image.ContentMode.Tile) {
            // fit=scale&w=width/scale&h=height/scale
            int newWidth = (int) Math.round(image.getWidth() / scale);
            int newHeight = (int) Math.round(image.getHeight() / scale);
            builder.appendQueryParameter("fit", "scale");
            builder.appendQueryParameter("w", Integer.toString(newWidth));
            builder.appendQueryParameter("h", Integer.toString(newHeight));
        } else if (mode == Image.ContentMode.Stretch) {
            // fit=scale&w=block.width&h=block.height
            builder.appendQueryParameter("fit", "scale");
            builder.appendQueryParameter("w", Integer.toString(blockWidth));
            builder.appendQueryParameter("h", Integer.toString(blockHeight));
        } else if (mode == Image.ContentMode.Fit) {
            // fit=fill&w=block.width&h=block.height

            builder.appendQueryParameter("fit", "fill");
            builder.appendQueryParameter("w", Integer.toString(blockWidth));
            builder.appendQueryParameter("h", Integer.toString(blockHeight));
        } else if (mode == Image.ContentMode.Fill) {
            // fit=scale
            builder.appendQueryParameter("fit", "scale");
            // first check width
            if ((int) image.getWidth() < blockWidth) {
                // width needs to increase
                int newWidth = blockWidth;
                int newHeight = (int) Math.round((image.getHeight() / image.getWidth()) * newWidth);

                if (newHeight >= blockHeight) {
                    builder.appendQueryParameter("w", Integer.toString(newWidth));
                    builder.appendQueryParameter("h", Integer.toString(newHeight));
                }
            }

            if ((int) image.getHeight() < blockHeight) {
                int newHeight = blockHeight;
                int newWidth = (int) Math.round((image.getWidth() / image.getHeight()) * newHeight);

                if (newWidth >= blockWidth) {
                    builder.appendQueryParameter("w", Integer.toString(newWidth));
                    builder.appendQueryParameter("h", Integer.toString(newHeight));
                }
            }

            if ((int) image.getWidth() > blockWidth) {
                int newWidth = blockWidth;
                int newHeight = (int) Math.round((image.getHeight() / image.getWidth()) * newWidth);

                if (newHeight <= blockHeight) {
                    builder.appendQueryParameter("w", Integer.toString(newWidth));
                    builder.appendQueryParameter("h", Integer.toString(newHeight));
                }
            }

            if ((int) image.getHeight() > blockWidth) {
                int newHeight = blockHeight;
                int newWidth = (int) Math.round((image.getHeight() / image.getWidth()) * newHeight);

                if (newWidth <= blockHeight) {
                    builder.appendQueryParameter("w", Integer.toString(newWidth));
                    builder.appendQueryParameter("h", Integer.toString(newHeight));
                }
            }
        }
        Log.i("OPTIMIZEDIMAGE", "getOptimizedImageUrl: " + builder.build().toString());
        return builder.build().toString();
    }
}
