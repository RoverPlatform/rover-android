package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ata_n on 2016-07-07.
 */
public class AssetManager {

    public interface AssetManagerListener {
        void onAssetSuccess(Bitmap bitmap);
        void onAssetFailure();
    }

    private static AssetManager sAssetManager;
    private Map<String, AssetDownloader> mDownloaders;

    public static AssetManager getSharedAssetManager(Context context) {
        if (sAssetManager == null) {
            sAssetManager = new AssetManager(context);
        }
        return sAssetManager;
    }

    private AssetManager(Context context) {
        mDownloaders = new HashMap<>();
    }

    public void cancelAsset(AssetManagerListener listener) {
        String key = listener.toString();
        AssetDownloader downloader = mDownloaders.get(key);
        if (downloader != null) {
            downloader.cancel(true);
            mDownloaders.remove(downloader);
        }
    }

    public void fetchAsset(String url, final AssetManagerListener listener) {
        if (url == null || listener == null) {
            return;
        }

        final String key = listener.toString();

        AssetDownloader remainingDownloader = mDownloaders.get(key);
        if (remainingDownloader != null) {
            remainingDownloader.cancel(true);
            mDownloaders.remove(url);
        }

        // TODO: setup cache
        // TODO: setup downloaders map so we dont download the same url multiple times



        AssetDownloader downloader = new AssetDownloader(new AssetDownloader.AssetDownloaderListener() {
            @Override
            public void onAssetDownloadSuccess(Bitmap bitmap) {
                mDownloaders.remove(key);
                listener.onAssetSuccess(bitmap);
            }

            @Override
            public void onAssetDownloadFailure() {
                mDownloaders.remove(key);
                listener.onAssetFailure();
            }
        });

        mDownloaders.put(key, downloader);

        downloader.execute(url);
    }


}
