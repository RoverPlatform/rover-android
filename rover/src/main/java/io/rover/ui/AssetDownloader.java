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
import java.net.URLEncoder;

/**
 * Created by Rover Labs Inc on 2016-07-07.
 */
public class AssetDownloader extends AsyncTask<String, Void, Bitmap> {

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
            // Check disk cache first


            String encodedUrl = URLEncoder.encode(urlString, "UTF-8");
            File file = new File(mCacheDir, encodedUrl);

            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }

            // Download Asset

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();

            // Write to cache
            OutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            input.close();
            outputStream.flush();
            outputStream.close();

            connection.disconnect();

            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("AssetDownloader", "Error downloading asset: " + e.getMessage());
            return null;
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