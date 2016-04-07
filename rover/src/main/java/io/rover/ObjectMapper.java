package io.rover;

import android.provider.SyncStateContract;
import android.util.Log;

import com.google.android.gms.location.Geofence;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ata_n on 2016-04-01.
 */
public class ObjectMapper implements JsonApiObjectMapper {
    @Override
    public Object getObject(String type, String identifier, JSONObject attributes) {

        try {
            return parseObject(type, identifier, attributes);
        } catch (JSONException e) {
            Log.w("ObjectMapper", "Error parsing json into object");
            e.printStackTrace();
        }

        return null;
    }

    private Object parseObject(String type, String id, JSONObject attributes) throws JSONException {
        switch (type) {
            case "events": {
                String object = attributes.getString("object");
                String action = attributes.getString("action");

                switch (object) {
                    case "location":
                        // just return the same object
                }
                break;
            }
            case "geofence-regions": {
                double lat = attributes.getDouble("latitude");
                double lng = attributes.getDouble("longitude");
                double radius = attributes.getDouble("radius");

                return getGeofence(id, lat, lng, (float) radius);
            }
            case "messages": {
                String title = attributes.getString("title");
                String text = attributes.getString("notification-text");
                String timestampString = attributes.getString("timestamp");
                Date timestamp = null;
                boolean read = attributes.getBoolean("read");
                String action = attributes.getString("action");

                // TODO: get this from Rover or somewhere else
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
                try {
                    timestamp = sdf.parse(timestampString);
                } catch (ParseException e) {
                    Log.e("ObjectMapper", "Error parsing date: " + timestampString);
                    return null;
                }

                Message message = new Message(title, text, timestamp, id);
                message.setRead(read);

                switch (action) {
                    case "link":
                        String url = attributes.getString("action-url");
                        message.setAction(Message.Action.Link);
                        try {
                            message.setURL(new URL(url));
                        } catch (MalformedURLException e) {
                            Log.e("ObjectMapper", "Bad action-url: " + url);
                        }
                        break;
                    default:
                        message.setAction(Message.Action.None);
                        break;
                }

                return message;
            }
        }

        return null;
    }

    private Geofence getGeofence(String id, double lattitude, double longitude, float radius) {
        return new Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(lattitude, longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }
}
