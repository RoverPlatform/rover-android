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
    private String mCacheDir;

    public static AssetManager getSharedAssetManager(Context context) {
        if (sAssetManager == null) {
            sAssetManager = new AssetManager(context);
        }
        return sAssetManager;
    }

    private AssetManager(Context context) {
        mCacheDir = context.getCacheDir().getAbsolutePath();
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

    public void fetchAsset(final String url, final AssetManagerListener listener) {
        if (url == null || listener == null) {
            return;
        }

        final String key = listener.toString();

        AssetDownloader remainingDownloader = mDownloaders.get(key);
        if (remainingDownloader != null) {
            remainingDownloader.cancel(true);
            mDownloaders.remove(url);
        }

        // Check caches

        final String cacheKey = url.toLowerCase();

//        Bitmap cachedBitmap = mMemoryCache.get(cacheKey);
//        if (cachedBitmap != null) {
//            listener.onAssetSuccess(cachedBitmap);
//            return;
//        }

        // TODO: setup downloaders map so we dont download the same url multiple times (This may no longer be required)

        // Setup asset download

        final AssetDownloader downloader = new AssetDownloader(new AssetDownloader.AssetDownloaderListener() {
            @Override
            public void onAssetDownloadSuccess(Bitmap bitmap) {
                mDownloaders.remove(key);
                //mMemoryCache.put(cacheKey, bitmap);
                listener.onAssetSuccess(bitmap);
            }

            @Override
            public void onAssetDownloadFailure() {
                mDownloaders.remove(key);
                //mMemoryCache.remove(cacheKey);
                listener.onAssetFailure();
            }
        }, mCacheDir);

        mDownloaders.put(key, downloader);

        downloader.execute(url);
    }


}
