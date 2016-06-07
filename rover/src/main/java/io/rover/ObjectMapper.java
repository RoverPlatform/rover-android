package io.rover;

import android.util.Log;

import com.google.android.gms.location.Geofence;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.Location;
import io.rover.model.Message;
import io.rover.network.JsonApiResponseHandler;

/**
 * Created by ata_n on 2016-04-01.
 */
public class ObjectMapper implements JsonApiResponseHandler.JsonApiObjectMapper {
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
                        break;
                    case "geofence-region":
                        Location location = getLocation(attributes.getJSONObject("location"));
                        GeofenceTransitionEvent event = null;
                        switch (action) {
                            case "enter":
                                event = new GeofenceTransitionEvent(null, Geofence.GEOFENCE_TRANSITION_ENTER, null);
                                event.setLocation(location);
                                break;
                            case "exit":
                                event = new GeofenceTransitionEvent(null, Geofence.GEOFENCE_TRANSITION_EXIT, null);
                                event.setLocation(location);
                                break;
                        }
                        return event;
                    case "beacon-region":
                        break;
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
                String title = attributes.getString("android-title");
                String text = attributes.getString("notification-text");
                String timestampString = attributes.getString("timestamp");
                Date timestamp = null;
                boolean read = attributes.getBoolean("read");
                String contentType = attributes.getString("content-type");

                // TODO: get this from Rover or somewhere else
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                try {
                    timestamp = sdf.parse(timestampString);
                } catch (ParseException e) {
                    Log.e("ObjectMapper", "Error parsing date: " + timestampString);
                    e.printStackTrace();
                    return null;
                }

                Message message = new Message(title, text, timestamp, id);
                message.setRead(read);

                switch (contentType) {
                    case "website":
                        String url = attributes.getString("website-url");
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

    private Location getLocation(JSONObject attributes) throws JSONException{
        JSONArray tagsArray = attributes.getJSONArray("tags");
        ArrayList<String> tags = new ArrayList<>(tagsArray.length());

        for (int i=0; i<tagsArray.length(); i++) {
            tags.set(i, tagsArray.getString(i));
        }

        return new Location(
                attributes.getDouble("latitude"),
                attributes.getDouble("longitude"),
                attributes.getDouble("radius"),
                attributes.getString("name"),
                tags);
    }
}
