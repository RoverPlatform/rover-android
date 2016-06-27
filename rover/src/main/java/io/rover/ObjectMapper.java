package io.rover;

import android.util.Log;

import com.google.android.gms.location.Geofence;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import io.rover.model.Alignment;
import io.rover.model.Block;
import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.Message;
import io.rover.model.Offset;
import io.rover.model.PercentageUnit;
import io.rover.model.Place;
import io.rover.model.PointsUnit;
import io.rover.model.Row;
import io.rover.model.Screen;
import io.rover.model.TextBlock;
import io.rover.model.Unit;
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
                        Place place = getPlace(attributes.getJSONObject("place"));
                        GeofenceTransitionEvent event = null;
                        switch (action) {
                            case "enter":
                                event = new GeofenceTransitionEvent(null, Geofence.GEOFENCE_TRANSITION_ENTER, null);
                                event.setPlace(place);
                                break;
                            case "exit":
                                event = new GeofenceTransitionEvent(null, Geofence.GEOFENCE_TRANSITION_EXIT, null);
                                event.setPlace(place);
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

                // TODO: get this from Rover or somewhere else also need correction to adjust for timezone ZZZZ
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
                    case "website": {
                        String uri = attributes.getString("website-url");
                        message.setAction(Message.Action.Website);
                        try {
                            message.setURI(new URI(uri));
                        } catch (URISyntaxException e) {
                            Log.e("ObjectMapper", "Bad action-uri: " + uri);
                        }
                        break;
                    }
                    case "landing-page": {
                        message.setAction(Message.Action.LandingPage);
                        if (!attributes.isNull("landing-page")) {
                            JSONObject screenAttributes = attributes.getJSONObject("landing-page");
                            Screen landingPage = (Screen) parseObject("screens", null, screenAttributes);
                            message.setLandingPage(landingPage);
                        }
                        break;
                    }
                    case "deep-link": {
                        String uri = attributes.getString("deep-link-url");
                        message.setAction(Message.Action.DeepLink);
                        try {
                            message.setURI(new URI(uri));
                        } catch (URISyntaxException e) {
                            Log.e("ObjectMapper", "Bad action-url: " + uri);
                        }
                        break;
                    }
                    default:
                        message.setAction(Message.Action.None);
                        break;
                }

                return message;
            }
            case "screens": {
                ArrayList<Row> rows = new ArrayList<>();
                JSONArray rowsAttributes = attributes.getJSONArray("rows");
                String title = attributes.getString("title");

                for (int i = 0; i < rowsAttributes.length(); i++) {
                    JSONObject rowAttributes = rowsAttributes.getJSONObject(i);
                    Row row = (Row) parseObject("rows", null, rowAttributes);
                    if (row != null) {
                        rows.add(row);
                    }
                }

                Screen screen = new Screen(rows);
                screen.setTitle(title);

                return screen;
            }
            case "rows": {
                ArrayList<Block> blocks = new ArrayList<>();
                JSONArray blocksAttributes = attributes.getJSONArray("blocks");

                for (int i = 0; i < blocksAttributes.length(); i++) {
                    JSONObject blockAttributes = blocksAttributes.getJSONObject(i);
                    Block block = (Block)parseObject("blocks", null, blockAttributes);
                    if (block != null) {
                        blocks.add(block);
                    }
                }

                Row row = new Row(blocks);
                if (!attributes.isNull("height")) {
                    JSONObject heightAttributes = attributes.getJSONObject("height");
                    Unit height = (Unit) parseObject("units", null, heightAttributes);

                    row.setHeight(height);
                }

                return row;
            }
            case "units": {
                if (attributes == null) {
                    return PointsUnit.ZeroUnit;
                }

                double value = attributes.getDouble("value");
                String unitType = attributes.getString("type");

                if (value == 0) { return PointsUnit.ZeroUnit; }

                switch (unitType) {
                    case "percentage":
                        return new PercentageUnit(value);
                    case "points":
                        return new PointsUnit(value);
                }

                return PointsUnit.ZeroUnit;
            }
            case "blocks": {
                Block block = new TextBlock();
                if (!attributes.isNull("width")) {
                    block.setWidth((Unit) parseObject("units", null, attributes.getJSONObject("width")));
                }
                if (!attributes.isNull("height")) {
                    block.setHeight((Unit) parseObject("units", null, attributes.getJSONObject("height")));
                }
                block.setAlignment((Alignment)parseObject("alignments", null, attributes.getJSONObject("alignment")));
                block.setOffset((Offset)parseObject("offsets", null, attributes.getJSONObject("offset")));
                String position = attributes.getString("position");
                if (position != null && position.equals("floating")) {
                    block.setPosition(Block.Position.Floating);
                } else {
                    block.setPosition(Block.Position.Stacked);
                }

                return block;
            }
            case "alignments": {
                Alignment.Vertical verticalAlignment;
                Alignment.Horizontal horizontalAlignment;
                String vertical = attributes.getString("vertical");
                String horizontal = attributes.getString("horizontal");

                if (vertical != null) {
                    switch (vertical) {
                        case "middle":
                            verticalAlignment = Alignment.Vertical.Middle;
                        case "bottom":
                            verticalAlignment = Alignment.Vertical.Bottom;
                        case "fill":
                            verticalAlignment = Alignment.Vertical.Fill;
                        default:
                            verticalAlignment = Alignment.Vertical.Top;
                    }
                } else {
                    verticalAlignment = Alignment.Vertical.Top;
                }

                if (horizontal != null) {
                    switch (horizontal) {
                        case "center":
                            horizontalAlignment = Alignment.Horizontal.Center;
                        case "right":
                            horizontalAlignment = Alignment.Horizontal.Right;
                        case "fill":
                            horizontalAlignment = Alignment.Horizontal.Fill;
                        default:
                            horizontalAlignment = Alignment.Horizontal.Left;
                    }
                } else {
                    horizontalAlignment = Alignment.Horizontal.Left;
                }

                return new Alignment(horizontalAlignment, verticalAlignment);
            }
            case "offsets": {
                Unit top = (Unit)parseObject("units", null, attributes.getJSONObject("top"));
                Unit right = (Unit)parseObject("units", null, attributes.getJSONObject("right"));
                Unit bottom = (Unit)parseObject("units", null, attributes.getJSONObject("bottom"));
                Unit left = (Unit)parseObject("units", null, attributes.getJSONObject("left"));
                Unit center = (Unit)parseObject("units", null, attributes.getJSONObject("center"));
                Unit middle = (Unit)parseObject("units", null, attributes.getJSONObject("middle"));

                return new Offset(top,right,bottom,left,center,middle);
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

    private Place getPlace(JSONObject attributes) throws JSONException{
        JSONArray tagsArray = attributes.getJSONArray("tags");
        ArrayList<String> tags = new ArrayList<>(tagsArray.length());

        for (int i=0; i<tagsArray.length(); i++) {
            tags.set(i, tagsArray.getString(i));
        }

        return new Place(
                attributes.getDouble("latitude"),
                attributes.getDouble("longitude"),
                attributes.getDouble("radius"),
                attributes.getString("name"),
                tags);
    }
}
