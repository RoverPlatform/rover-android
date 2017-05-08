package io.rover.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;


import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import io.rover.Rover;

import io.rover.util.DataUri;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class AssetManager {
    private static final String TAG = "AssetManager";



    public interface AssetManagerListener {
        void onAssetSuccess(Bitmap bitmap);
        void onAssetFailure();
    }

    private static AssetManager sAssetManager;
    private Map<String, AssetDownloader> mDownloaders;
    private String mCacheDir;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private LruCache<String, Bitmap> mBitmapLruCache;

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


        /*
            Try to parse the url as a data-uri string. If we fail to parse as a data-uri just move on and let
            the default AssetDownloadManager get the asset using HTTP
            Specs: https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs
         */
        DataUri dataUri;

        try {
            dataUri = new DataUri(url);
            byte[] decodedString;

            if (dataUri.getEncodingType().equals("base64")) {
                decodedString = Base64.decode(dataUri.getData(), Base64.DEFAULT);
            } else {
                decodedString = dataUri.getData().getBytes();
            }

            final Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            if (bitmap == null) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onAssetFailure();
                    }
                });

                return;
            } else {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onAssetSuccess(bitmap);
                    }
                });

                return;
            }

        } catch (MalformedURLException e) {
            // This isn't a datauri lets just continue
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
            /*
                Make sure our callback is running on the main thread
                The listeners are usually modifying ui elements
             */
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onAssetSuccess(cachedImage);
                }
            });

            /*
                Cached image was found make sure to break execution and return
             */
            return;
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
