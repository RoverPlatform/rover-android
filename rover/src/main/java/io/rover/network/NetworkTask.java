package io.rover.network;

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

import io.rover.Rover;

/**
 * Created by ata_n on 2016-03-23.
 */
public class NetworkTask implements Runnable {

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

    public interface ResponseHandler {
        void onHandleResponse(InputStreamReader reader) throws IOException;
    }

    public interface NetworkTaskConnectionManager {
        void onPrepareConnection(HttpURLConnection connection);
    }

    private URL mURL;
    private String mMethod;

    //private JsonPayloadProvider mPayloadProvider;
    //private JsonResponseHandler mResponseHandler;

    private PayloadProvider mPayloadProvider;
    private ResponseHandler mResponseHandler;
    private NetworkTaskConnectionManager mConnectionManager;

    public NetworkTask(String method, URL url) {
        mMethod = method;
        mURL = url;
    }

    public void setPayloadProvider(PayloadProvider payloadProvider) {
        mPayloadProvider = payloadProvider;
    }

    public void setResponseHandler(ResponseHandler completionHandler) {
        mResponseHandler = completionHandler;
    }

    public void setConnectionManager(NetworkTaskConnectionManager manager) {
        mConnectionManager = manager;
    }

    @Override
    public void run() {

        InputStream is = null;

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

            int status = connection.getResponseCode();

            Log.i("NetworkTask", "HTTP Status: " + status);

            is = connection.getInputStream();

            if (mResponseHandler != null) {
                //JsonReader jsonReader = new JsonReader(new InputStreamReader(is, "UTF-8"));
                //mResponseHandler.onHandleResponse(jsonReader);
                mResponseHandler.onHandleResponse(new InputStreamReader(is, "UTF-8"));
            }

        } catch (IOException e) {

            if (connection != null) {
                is = connection.getErrorStream();
            }

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
