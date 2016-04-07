package io.rover;

import android.os.AsyncTask;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by ata_n on 2016-03-23.
 */
public class NetworkTask implements Runnable {

    private URL mURL;
    private String mMethod;

    private JsonPayloadProvider mPayloadProvider;
    private JsonResponseHandler mResponseHandler;

    public NetworkTask(String method, URL url) {
        mMethod = method;
        mURL = url;
    }

    public void setPayloadProvider(JsonPayloadProvider payloadProvider) {
        mPayloadProvider = payloadProvider;
    }

    public void setResponseHandler(JsonResponseHandler completionHandler) {
        mResponseHandler = completionHandler;
    }

    @Override
    public void run() {

        InputStream is = null;

        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)mURL.openConnection();
            connection.setRequestMethod(mMethod);
            connection.setRequestProperty("Accept", "application/vn.api+json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Rover-Api-Key", Rover.getApplicationToken());
            connection.setRequestProperty("X-Rover-Device-Id", "DEVICE_ID");

            connection.setDoInput(true);

            if (mPayloadProvider != null) {
                connection.setDoOutput(true);

                JsonWriter writer = new JsonWriter(new OutputStreamWriter(connection.getOutputStream()));
                writer.setIndent("  ");

                mPayloadProvider.onProvidePayload(writer);

                writer.close();
            }

            connection.connect();

            int status = connection.getResponseCode();

            Log.i("NetworkTask", "HTTP Status: " + status);

            is = connection.getInputStream();

            if (mResponseHandler != null) {
                JsonReader jsonReader = new JsonReader(new InputStreamReader(is, "UTF-8"));
                mResponseHandler.onHandleResponse(jsonReader);
            }

        } catch (IOException e) {
            is = connection.getErrorStream();

            String error = getStringFromInputStream(is);
            Log.e("NetworkTask", error);
            Log.e("NetworkTask", "Error making HTTP connection: ");
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Silence this exception from closing the stream
                }
            }
        }

    }

    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();

    }

}

interface JsonPayloadProvider {
    void onProvidePayload(JsonWriter writer) throws IOException;
}

interface JsonResponseHandler {
    void onHandleResponse(JsonReader reader) throws IOException;
}
