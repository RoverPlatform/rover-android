package io.rover.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by ata_n on 2016-07-07.
 */
public class AssetDownloader extends AsyncTask<String, Void, Bitmap> {

    public interface AssetDownloaderListener {
        void onAssetDownloadSuccess(Bitmap bitmap);
        void onAssetDownloadFailure();
    }

    private AssetDownloaderListener mListener;

    public AssetDownloader(AssetDownloaderListener listener) {
        mListener = listener;
    }

    @Override
    protected Bitmap doInBackground(String... params) {
        String urlString = params[0];
        if (urlString == null) {
            return null;
        }

        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            return BitmapFactory.decodeStream(input);
        } catch (IOException e) {
            Log.e("AssetDownloader", "Error downloading asset: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap == null) {
            mListener.onAssetDownloadFailure();
            return;
        }

        mListener.onAssetDownloadSuccess(bitmap);
    }
}