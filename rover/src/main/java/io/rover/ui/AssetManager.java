package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ata_n on 2016-07-07.
 */
public class AssetManager {

    public interface AssetManagerListener {
        void onAssetSuccess(Bitmap bitmap);
        void onAssetFailure();
    }

    private static AssetManager sAssetManager;

    public static AssetManager getSharedAssetManager(Context context) {
        if (sAssetManager == null) {
            sAssetManager = new AssetManager(context);
        }
        return sAssetManager;
    }

    private AssetManager(Context context) {

    }

    public void fetchAsset(String url, final AssetManagerListener listener) {
        if (url == null || listener == null) {
            return;
        }

        // TODO: setup cache
        // TODO: setup downloaders map so we dont download the same url multiple times

        new AssetDownloader(new AssetDownloader.AssetDownloaderListener() {
            @Override
            public void onAssetDownloadSuccess(Bitmap bitmap) {
                listener.onAssetSuccess(bitmap);
            }

            @Override
            public void onAssetDownloadFailure() {
                listener.onAssetFailure();
            }
        }).execute(url);
    }


}
