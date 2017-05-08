package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;

import io.rover.Rover;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class AssetManager {
    private static final String TAG = "AssetManager";

    private LruCache<String, Bitmap> mBitmapLruCache;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface AssetManagerListener {
        void onAssetSuccess(Bitmap bitmap);
        void onAssetFailure();
    }

    private static AssetManager sAssetManager;
    private Map<String, AssetDownloader> mDownloaders;
    private String mCacheDir;

    public synchronized static AssetManager getSharedAssetManager(Context context) {
        if (sAssetManager == null) {
            sAssetManager = new AssetManager(context, Rover.getConfig().getImageCacheSize());
        }
        return sAssetManager;
    }

    private AssetManager(Context context, int memoryCacheSize) {
        mCacheDir = context.getCacheDir().getAbsolutePath();
        mDownloaders = new HashMap<>();


        // Memory Cache is stored in kilobytes
        Log.d(TAG, "Using: " + memoryCacheSize + "kb for image caching");
        mBitmapLruCache = new LruCache<String, Bitmap>(memoryCacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    return value.getAllocationByteCount() / 1024;
                } else {
                    return value.getByteCount() / 1024;
                }
            }
        };
    }

    public void flushMemoryCache() {
        mBitmapLruCache.evictAll();
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

        final Bitmap cachedImage = mBitmapLruCache.get(cacheKey);

        if (cachedImage != null) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onAssetSuccess(cachedImage);
                }
            });
        }

        // Setup asset download

        final AssetDownloader downloader = new AssetDownloader(new AssetDownloader.AssetDownloaderListener() {
            @Override
            public void onAssetDownloadSuccess(Bitmap bitmap) {
                mDownloaders.remove(key);
                mBitmapLruCache.put(cacheKey, bitmap);
                listener.onAssetSuccess(bitmap);
            }

            @Override
            public void onAssetDownloadFailure() {
                mDownloaders.remove(key);
                listener.onAssetFailure();
            }
        }, mCacheDir);

        mDownloaders.put(key, downloader);

        downloader.execute(url);
    }


}
