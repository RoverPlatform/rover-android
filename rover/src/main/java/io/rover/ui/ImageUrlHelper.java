package io.rover.ui;

import android.net.Uri;
import android.support.annotation.NonNull;
import io.rover.model.Image;
import io.rover.util.DataUri;

/**
 * Created by Rover Labs Inc on 2016-10-12.
 */
public class ImageUrlHelper {
    public static String getOptimizedImageUrl(int blockWidth, int blockHeight, @NonNull Image image, Image.ContentMode mode, Double scale) {

        /* Do not try to optimize the url if its a data uri */

        if (DataUri.isDataUri(image.getImageUrl())) {
            return image.getImageUrl();
        }

        if ((image.getWidth() < blockWidth) || blockWidth == 0 || blockHeight == 0) {
            // Don't optimize that is already smaller
            return image.getImageUrl();
        }


        int newWidth = 0;
        int newHeight = 0;

        Uri.Builder builder = Uri.parse(image.getImageUrl()).buildUpon();
        if (mode == Image.ContentMode.Original || mode == Image.ContentMode.Tile) {
            return image.getImageUrl();
        } else if (mode == Image.ContentMode.Stretch) {
            // fit=scale&w=block.width&h=block.height
            builder.appendQueryParameter("fit", "scale");

            newWidth = blockWidth;
            newHeight = blockHeight;
        } else if (mode == Image.ContentMode.Fit) {
            // fit=fill&w=block.width&h=block.height

            builder.appendQueryParameter("fit", "scale");

            newWidth = blockWidth;
            newHeight = (int)(blockHeight / image.getAspectRatio());

            if (newHeight > blockHeight) {
                newHeight = blockHeight;
                newWidth = (int)(newHeight * image.getAspectRatio());
            }

        } else if (mode == Image.ContentMode.Fill) {
            // fit=scale
            builder.appendQueryParameter("fit", "scale");

            newWidth = blockWidth;
            newHeight = (int)(blockWidth / image.getAspectRatio());

            if (newHeight < blockHeight) {
                newHeight = blockHeight;
                newWidth = (int)(newHeight * image.getAspectRatio());
            }
        }

        if (newWidth > image.getWidth()) {
            return image.getImageUrl();
        }

        builder.appendQueryParameter("w", Integer.toString(newWidth));
        builder.appendQueryParameter("h", Integer.toString(newHeight));
        
        return builder.build().toString();
    }
}
