package io.rover.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class AssetDownloader extends AsyncTask<String, Void, Bitmap> {

    private static final String TAG = "AssetDownloader";

    public interface AssetDownloaderListener {
        void onAssetDownloadSuccess(Bitmap bitmap);
        void onAssetDownloadFailure();
    }

    private AssetDownloaderListener mListener;
    private String mCacheDir;

    public AssetDownloader(AssetDownloaderListener listener, String cacheDir) {
        mListener = listener;
        mCacheDir = cacheDir;
    }

    @Override
    protected Bitmap doInBackground(String... params) {
        String urlString = params[0];
        if (urlString == null) {
            return null;
        }

        try {

            String cacheKey = getCachekey(urlString);

            if (cacheKey != null) {
                // Check disk cache first

                File file = new File(mCacheDir, cacheKey);

                if (file.exists()) {
                    return BitmapFactory.decodeFile(file.getAbsolutePath());
                }
            }

            // Download Asset

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();

            if (cacheKey != null) {
                File file = new File(mCacheDir, cacheKey);
                saveFileToCache(input, file);
                connection.disconnect();
                input.close();
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            } else {
                // we are unable to save file to disk so we will just convert the input stream to a Bitmap
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                connection.disconnect();
                return bitmap;
            }

        } catch (Exception e) {
            Log.e("AssetDownloader", "Error downloading asset: " + e.getMessage());
            return null;
        }
    }

    private String getCachekey(String url) {

        if (url == null) {
            return null;
        }

        MessageDigest digester = null;

        try {
            digester = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        byte [] messageDigest = digester.digest(url.getBytes());

        StringBuffer hexString = new StringBuffer();

        for (int i = 0; i < messageDigest.length; i++) {
            String hex = Integer.toHexString(0xFF & messageDigest[i]);

            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        return hexString.toString();
    }

    private void saveFileToCache(InputStream input, File file) {
        // Write to cache
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            Log.w(TAG, "Unable to cache file: " + file.getPath(), e);
        }
    }


    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (this.isCancelled()) {
            return;
        }

        if (bitmap == null) {
            mListener.onAssetDownloadFailure();
            return;
        }

        mListener.onAssetDownloadSuccess(bitmap);
    }


}