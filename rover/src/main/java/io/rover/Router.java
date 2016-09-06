package io.rover;

import android.net.NetworkRequest;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import io.rover.model.Device;
import io.rover.network.NetworkTask;

/**
 * Created by ata_n on 2016-04-19.
 */
class Router implements NetworkTask.NetworkTaskConnectionManager {

    private static Router sharedInstance = new Router();
    private static String baseURL = "https://api.rover.io/v1";
    private static String apiKey;
    private static String deviceId;

    private Router() {}

    static void setApiKey(String key) {
        apiKey = key;
    }

    static void setDeviceId(String id) {
        deviceId = id;
    }

    static NetworkTask getInboxNetworkTask() {
        String url = baseURL + "/inbox";
        try {
            NetworkTask networkTask = new NetworkTask("GET", new URL(url));
            networkTask.setConnectionManager(sharedInstance);
            return networkTask;
        } catch (MalformedURLException e) {
            Log.e("Router", "Bad URL: " + url);
            return null;
        }
    }

    static NetworkTask getEventsNetworkTask() {
        String url = baseURL + "/events";
        try {
            NetworkTask networkTask = new NetworkTask("POST", new URL(url));
            networkTask.setConnectionManager(sharedInstance);
            return networkTask;
        } catch (MalformedURLException e) {
            Log.e("Router", "Bad URL: " + url);
            return null;
        }
    }

    static NetworkTask getLandingPageNetworkTask(String messageId) {
        String url = baseURL + "/inbox/" + messageId + "/landing-page";
        try {
            NetworkTask networkTask = new NetworkTask("GET", new URL(url));
            networkTask.setConnectionManager(sharedInstance);
            return networkTask;
        } catch (MalformedURLException e) {
            Log.e("Router", "Bad URL: " + url);
            return null;
        }
    }

    static NetworkTask getExperienceNetworkTask(String experienceId) {
        String url = baseURL + "/experiences/" + experienceId;
        try {
            NetworkTask networkTask = new NetworkTask("GET", new URL(url));
            networkTask.setConnectionManager(sharedInstance);
            return networkTask;
        } catch (MalformedURLException e) {
            Log.e("Router", "Bad URL: " + url);
            return null;
        }
    }

    @Override
    public void onPrepareConnection(HttpURLConnection connection) {
        connection.setRequestProperty("X-Rover-Api-Key", apiKey);
        connection.setRequestProperty("X-Rover-Device-Id", deviceId);
    }
}
