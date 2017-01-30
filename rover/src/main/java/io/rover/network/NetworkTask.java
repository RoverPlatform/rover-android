package io.rover.network;

import android.support.annotation.Nullable;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import io.rover.Rover;

/**
 * Created by ata_n on 2016-03-23.
 */
public class NetworkTask {

//    public interface JsonPayloadProvider {
//        void onProvidePayload(JsonWriter writer) throws IOException;
//        void onPrepareConnection(HttpURLConnection connection);
//    }
//
//    public interface JsonResponseHandler {
//        void onHandleResponse(JsonReader reader) throws IOException;
//    }

    public interface PayloadProvider {
        void onPrepareConnection(HttpURLConnection connection);
        void onProvidePayload(OutputStreamWriter writer) throws IOException;
    }

    public interface NetworkTaskConnectionManager {
        void onPrepareConnection(HttpURLConnection connection);
    }

    private URL mURL;
    private String mMethod;

    private boolean mTaskFailed;
    private String mTaskFailureMessage;

    //private JsonPayloadProvider mPayloadProvider;
    //private JsonResponseHandler mResponseHandler;

    private PayloadProvider mPayloadProvider;
    private NetworkTaskConnectionManager mConnectionManager;

    public NetworkTask(String method, URL url) {
        mMethod = method;
        mURL = url;
        mTaskFailed = false;
    }

    public void setPayloadProvider(PayloadProvider payloadProvider) {
        mPayloadProvider = payloadProvider;
    }

    public void setConnectionManager(NetworkTaskConnectionManager manager) {
        mConnectionManager = manager;
    }

    public boolean hasTaskFailed() {
        return mTaskFailed;
    }

    public String getTaskFailureMessage() {
        return mTaskFailureMessage;
    }

    @Nullable
    public HttpResponse run() {

        InputStream is = null;
        HttpResponse response = null;

        HttpURLConnection connection = null;

        try {

            Log.i("NetworkTask", "Connection to: " + mMethod + " " + mURL.toString());

            connection = (HttpURLConnection)mURL.openConnection();
            connection.setUseCaches(true);
            connection.setRequestMethod(mMethod);
            //connection.setRequestProperty("Content-Type", "application/json");

            if (mConnectionManager != null) {
                mConnectionManager.onPrepareConnection(connection);
            }

            connection.setDoInput(true);

            if (mPayloadProvider != null) {

                mPayloadProvider.onPrepareConnection(connection);

                connection.setDoOutput(true);


                OutputStream outputStream = connection.getOutputStream();
                mPayloadProvider.onProvidePayload(new OutputStreamWriter(outputStream));


//                JsonWriter writer = new JsonWriter(new OutputStreamWriter(connection.getOutputStream()));
//                writer.setIndent("  ");
//
//                mPayloadProvider.onProvidePayload(writer);

                //writer.close();
            }

            connection.connect();

            response = new HttpResponse();
            response.setStatus(connection.getResponseCode());

            try {
                is = connection.getInputStream();
                response.setBody(new InputStreamReader(is, "UTF-8"));
            } catch (Exception e) {
                Log.w("NetworkTask", "Failed to get input stream");
            }

            Log.i("NetworkTask", "HTTP Status: " + response.getStatus());

        } catch (IOException e) {

            if (connection != null) {
                is = connection.getErrorStream();
            }

            mTaskFailed = true;

            String error = getStringFromInputStream(is);
            mTaskFailureMessage = error;

            Log.e("NetworkTask", error);

            try {
                if(is != null)
                    is.close();
            } catch (IOException ignored) {

            }
        }

        return response;
    }

    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            if (is == null) {
                return " - No stream -";
            }

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
