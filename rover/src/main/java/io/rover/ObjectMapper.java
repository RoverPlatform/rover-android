package io.rover;

import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.nearby.messages.Strategy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import io.rover.model.Action;
import io.rover.model.Alignment;
import io.rover.model.Appearance;
import io.rover.model.BarcodeBlock;
import io.rover.model.Block;
import io.rover.model.ButtonBlock;
import io.rover.model.CustomKeys;
import io.rover.model.Experience;
import io.rover.model.Font;
import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.Image;
import io.rover.model.ImageBlock;
import io.rover.model.Inset;
import io.rover.model.Message;
import io.rover.model.Offset;
import io.rover.model.PercentageUnit;
import io.rover.model.Place;
import io.rover.model.PointsUnit;
import io.rover.model.Row;
import io.rover.model.Screen;
import io.rover.model.TextBlock;
import io.rover.model.Unit;
import io.rover.model.WebBlock;
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
                    case "experience": {
                        String experienceId = attributes.getString("experience-id");
                        message.setAction(Message.Action.Experience);
                        message.setExperienceId(experienceId);
                        break;
                    }
                    default:
                        message.setAction(Message.Action.None);
                        break;
                }

                HashMap<String, String> propertiesMap = new HashMap<>();

                if (attributes.has("properties")) {
                    JSONObject properties = attributes.getJSONObject("properties");
                    Iterator<String> keys = properties.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        String value = properties.getString(key);
                        propertiesMap.put(key, value);
                    }
                }

                message.setProperties(propertiesMap);

                return message;
            }
            case "custom-keys": {
                CustomKeys customKeys = new CustomKeys(0);
                Iterator<String> iterator = attributes.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    String value = attributes.optString(key, "");
                    customKeys.put(key, value);
                }
                return customKeys;
            }
            case "screens": {
                ArrayList<Row> rows = new ArrayList<>();
                JSONArray rowsAttributes = attributes.getJSONArray("rows");

                for (int i = 0; i < rowsAttributes.length(); i++) {
                    JSONObject rowAttributes = rowsAttributes.getJSONObject(i);
                    Row row = (Row) parseObject("rows", null, rowAttributes);
                    if (row != null) {
                        rows.add(row);
                    }
                }

                Screen screen = new Screen(rows);
                if (!attributes.isNull("title")) {
                    screen.setTitle(attributes.getString("title"));
                }

                screen.setBackgroundColor(getColorFromJSON(attributes.getJSONObject("background-color")));
                screen.setTitleColor(getColorFromJSON(attributes.getJSONObject("title-bar-text-color")));
                screen.setActionBarColor(getColorFromJSON(attributes.getJSONObject("title-bar-background-color")));
                screen.setActionItemColor(getColorFromJSON(attributes.getJSONObject("title-bar-button-color")));
                if (attributes.has("status-bar-color")) {
                    screen.setStatusBarColor(getColorFromJSON(attributes.getJSONObject("status-bar-color")));
                }
                screen.setUseDefaultActionBarStyle(attributes.getBoolean("use-default-title-bar-style"));
                if (attributes.has("background-image") && !attributes.isNull("background-image")) {
                    screen.setBackgroundImage((Image)parseObject("images", null, attributes.getJSONObject("background-image")));
                }
                if (attributes.has("background-content-mode") && !attributes.isNull("background-content-mode")) {
                    screen.setBackgroundContentMode(getContentModeFromString(attributes.getString("background-content-mode")));
                }
                if (attributes.has("background-scale") && !attributes.isNull("background-scale")) {
                    screen.setBackgroundScale(attributes.getDouble("background-scale"));
                }
                if (attributes.has("title-bar-buttons") && !attributes.isNull("title-bar-buttons")) {
                    screen.setBarButtons(getBarButtons(attributes.getString("title-bar-buttons")));
                }

                String statusBarStyle = attributes.getString("status-bar-style");
                if (!statusBarStyle.equals("light")) {
                    screen.setStatusBarLight(true);
                }

                if (attributes.has("id"))
                    screen.setId(attributes.getString("id"));

                if (attributes.has("custom-keys")) {
                    CustomKeys keys = (CustomKeys) parseObject("custom-keys", null, attributes.getJSONObject("custom-keys"));
                    screen.setCustomKeys(keys);
                }
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

                if (attributes.has("background-color") && !attributes.isNull("background-color")) {
                    row.getBackgroundBlock().setBackgroundColor(getColorFromJSON(attributes.getJSONObject("background-color")));
                }
                if (attributes.has("background-image") && !attributes.isNull("background-image")) {
                    row.getBackgroundBlock().setBackgroundImage((Image) parseObject("images", null, attributes.getJSONObject("background-image")));
                }
                if (attributes.has("background-scale") && !attributes.isNull("background-scale")) {
                    row.getBackgroundBlock().setBackgroundScale(attributes.getDouble("background-scale"));
                }
                if (attributes.has("background-content-mode") && !attributes.isNull("background-content-mode")) {
                    row.getBackgroundBlock().setBackgroundContentMode(getContentModeFromString(attributes.getString("background-content-mode")));
                }
                if (attributes.has("auto-height") && attributes.getBoolean("auto-height")) {
                    row.setHeight(null);
                }

                if (attributes.has("custom-keys")) {
                    CustomKeys keys = (CustomKeys) parseObject("custom-keys", null, attributes.getJSONObject("custom-keys"));
                    row.setCustomKeys(keys);
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
                Block block = null;

                String blockType = attributes.getString("type");

                switch (blockType) {
                    case "barcode-block": {
                        String barcodeText = attributes.getString("barcode-text");
                        String barcodeType = attributes.getString("barcode-type");

                        block = new BarcodeBlock();
                        ((BarcodeBlock) block).setBarcodeText(barcodeText);
                        ((BarcodeBlock) block).setBarcodeType(barcodeType);

                        if (!attributes.isNull("image")) {
                            ((BarcodeBlock) block).setImage((Image) parseObject("images", null, attributes.getJSONObject("image")));
                        }

                        break;
                    }
                    case "image-block": {
                        block = new ImageBlock();
                        if (!attributes.isNull("image")) {
                            ((ImageBlock) block).setImage((Image) parseObject("images", null, attributes.getJSONObject("image")));
                        }
                        break;
                    }
                    case "text-block": {
                        block = new TextBlock();
                        ((TextBlock)block).setText(attributes.getString("text"));
                        Object textAlignment = attributes.get("text-alignment");
                        if (textAlignment instanceof JSONObject) {
                            ((TextBlock) block).setTextAlignment((Alignment) parseObject("alignments", null, (JSONObject) textAlignment));
                        } else if (textAlignment instanceof String) {
                            switch ((String)textAlignment) {
                                case "left": {
                                    ((TextBlock) block).setTextAlignment(new Alignment(Alignment.Horizontal.Left, Alignment.Vertical.Top));
                                    break;
                                }
                                case "right": {
                                    ((TextBlock) block).setTextAlignment(new Alignment(Alignment.Horizontal.Right, Alignment.Vertical.Top));
                                    break;
                                }
                                case "center": {
                                    ((TextBlock) block).setTextAlignment(new Alignment(Alignment.Horizontal.Center, Alignment.Vertical.Top));
                                    break;
                                }
                            }
                        }
                        ((TextBlock)block).setTextColor(getColorFromJSON(attributes.getJSONObject("text-color")));
                        if (attributes.has("text-offset")) {
                            ((TextBlock) block).setTextOffset((Offset) parseObject("offsets", null, attributes.getJSONObject("text-offset")));
                        }
                        ((TextBlock)block).setFont((Font) parseObject("fonts", null, attributes.getJSONObject("text-font")));
                        break;
                    }
                    case "button-block": {
                        block = new ButtonBlock();
                        JSONObject states = attributes.getJSONObject("states");
                        ((ButtonBlock)block).setAppearance((Appearance) parseObject("appearances", null, states.getJSONObject("normal")), ButtonBlock.State.Normal);
                        ((ButtonBlock)block).setAppearance((Appearance) parseObject("appearances", null, states.getJSONObject("highlighted")), ButtonBlock.State.Highlighted);
                        ((ButtonBlock)block).setAppearance((Appearance) parseObject("appearances", null, states.getJSONObject("selected")), ButtonBlock.State.Selected);
                        ((ButtonBlock)block).setAppearance((Appearance) parseObject("appearances", null, states.getJSONObject("disabled")), ButtonBlock.State.Disabled);
                        break;
                    }
                    case "web-view-block": {
                        block = new WebBlock();
                        ((WebBlock) block).setURL(attributes.getString("url"));
                        ((WebBlock) block).setScrollable(attributes.getBoolean("scrollable"));
                        break;
                    }
                    default:
                        block = new Block();
                }

                if (attributes.has("id")) {
                    block.setId(attributes.getString("id"));
                }

                if (attributes.has("action") && !attributes.isNull("action")) {
                    JSONObject actionAttributes = attributes.getJSONObject("action");
                    block.setAction((Action) parseObject("actions", null, actionAttributes));
                }

                // Appearance
                if (!(block instanceof ButtonBlock)) {
                    block.setBackgroundColor(getColorFromJSON(attributes.getJSONObject("background-color")));
                    if (attributes.has("border-color")) { // TODO: this shouldnt be needed
                        block.setBorderColor(getColorFromJSON(attributes.getJSONObject("border-color")));
                        block.setBorderRadius(attributes.getDouble("border-radius"));
                        block.setBorderWidth(attributes.getDouble("border-width"));
                    }
                }
                if (attributes.has("opacity")) {
                    block.setOpacity(attributes.getDouble("opacity"));
                }
                if (attributes.has("auto-height") && attributes.getBoolean("auto-height")) {
                    block.setHeight(null);
                }

                // Layout
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

                if (attributes.has("inset")) {
                    block.setInset((Inset)parseObject("insets", null, attributes.getJSONObject("inset")));
                } else {
                    block.setInset(Inset.ZeroInset);
                }

                if (attributes.has("auto-height") && attributes.getBoolean("auto-height")) {
                    block.setHeight(null);
                }


                // BackgroundImage

                if (attributes.has("background-image") && !attributes.isNull("background-image")) {
                    block.setBackgroundImage((Image)parseObject("images", null, attributes.getJSONObject("background-image")));
                }
                if (attributes.has("background-content-mode") && !attributes.isNull("background-content-mode")) {
                    block.setBackgroundContentMode(getContentModeFromString(attributes.getString("background-content-mode")));
                }
                if (attributes.has("background-scale") && !attributes.isNull("background-scale")) {
                    block.setBackgroundScale(attributes.getDouble("background-scale"));
                }

                if (attributes.has("custom-keys")) {
                    CustomKeys keys = (CustomKeys) parseObject("custom-keys", null, attributes.getJSONObject("custom-keys"));
                    block.setCustomKeys(keys);
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
                            break;
                        case "bottom":
                            verticalAlignment = Alignment.Vertical.Bottom;
                            break;
                        case "fill":
                            verticalAlignment = Alignment.Vertical.Fill;
                            break;
                        default:
                            verticalAlignment = Alignment.Vertical.Top;
                            break;
                    }
                } else {
                    verticalAlignment = Alignment.Vertical.Top;
                }

                if (horizontal != null) {
                    switch (horizontal) {
                        case "center":
                            horizontalAlignment = Alignment.Horizontal.Center;
                            break;
                        case "right":
                            horizontalAlignment = Alignment.Horizontal.Right;
                            break;
                        case "fill":
                            horizontalAlignment = Alignment.Horizontal.Fill;
                            break;
                        default:
                            horizontalAlignment = Alignment.Horizontal.Left;
                            break;
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
            case "images": {
                double width = attributes.getDouble("width");
                double height = attributes.getDouble("height");
                String urlString = attributes.getString("url");

                return new Image(width, height, urlString);
            }
            case "fonts": {
                float size = (float) attributes.getDouble("size");
                int weight = attributes.getInt("weight");

                return new Font(size, weight);
            }
            case "actions": {
                String actionType = attributes.getString("type");

                switch (actionType) {
                    case "open-url":
                    case "website-action":
                    case "deep-link-action": {
                        String actionUrl = attributes.getString("url");
                        return new Action(actionType, actionUrl);
                    }
                    case "go-to-screen":{
                        String screenId = attributes.getString("screen-id");
                        return new Action(actionType, screenId);
                    }
                }
            }
            case "appearances": {
                Appearance appearance = new Appearance();
                appearance.title = attributes.getString("text");
                Object titleAlignment = attributes.get("text-alignment");
                if (titleAlignment instanceof JSONObject) {
                    appearance.titleAlignment = (Alignment) parseObject("alignments", null, (JSONObject) titleAlignment);
                } else if (titleAlignment instanceof String) {
                    switch ((String)titleAlignment) {
                        case "left": {
                            appearance.titleAlignment = new Alignment(Alignment.Horizontal.Left, Alignment.Vertical.Middle);
                            break;
                        }
                        case "right": {
                            appearance.titleAlignment = new Alignment(Alignment.Horizontal.Right, Alignment.Vertical.Middle);
                            break;
                        }
                        case "center": {
                            appearance.titleAlignment = new Alignment(Alignment.Horizontal.Center, Alignment.Vertical.Middle);
                            break;
                        }
                    }
                }
                if (attributes.has("text-offset")) {
                    appearance.titleOffset = (Offset) parseObject("offsets", null, attributes.getJSONObject("text-offset"));
                }
                appearance.titleColor = getColorFromJSON(attributes.getJSONObject("text-color"));
                appearance.titleFont = (Font) parseObject("fonts", null, attributes.getJSONObject("text-font"));
                appearance.backgroundColor = getColorFromJSON(attributes.getJSONObject("background-color"));
                appearance.borderColor = getColorFromJSON(attributes.getJSONObject("border-color"));
                appearance.borderWidth = attributes.getDouble("border-width");
                appearance.borderRadius = attributes.getDouble("border-radius");
                return appearance;
            }
            case "experiences": {
                JSONArray screensAttributes = attributes.getJSONArray("screens");
                String homeScreenId = attributes.getString("home-screen-id");

                ArrayList<Screen> screens = new ArrayList<>();
                for (int i = 0; i < screensAttributes.length(); i++) {
                    JSONObject screenAttributes = screensAttributes.getJSONObject(i);
                    Screen screen = (Screen) parseObject("screens", null, screenAttributes);
                    if (screen != null) {
                        screens.add(screen);
                    }
                }

                Experience experience = new Experience(screens, homeScreenId, id);
                if (attributes.has("version-id")) {
                    experience.setVersion(attributes.getString("version-id"));
                }

                if (attributes.has("custom-keys")) {
                    CustomKeys keys = (CustomKeys) parseObject("custom-keys", null, attributes.getJSONObject("custom-keys"));
                    experience.setCustomKeys(keys);
                }

                return experience;
            }
            case "insets": {
                return new Inset(attributes.getInt("top"), attributes.getInt("right"), attributes.getInt("bottom"), attributes.getInt("left"));
            }
        }

        return null;
    }

    private Geofence getGeofence(String id, double lattitude, double longitude, float radius) {
        if (id == null) {
            return null;
        }

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
            tags.add(i, tagsArray.getString(i));
        }

        return new Place(
                attributes.getDouble("latitude"),
                attributes.getDouble("longitude"),
                attributes.getDouble("radius"),
                attributes.getString("name"),
                tags);
    }

    private int getColorFromJSON(JSONObject attributes) {
        try {
            double red = attributes.getDouble("red");
            double blue = attributes.getDouble("blue");
            double green = attributes.getDouble("green");
            double alpha = attributes.getDouble("alpha");

            return Color.argb((int)(alpha * 255), (int)red, (int)green, (int)blue);
        } catch (JSONException e) {
            return 0;
        }
    }

    private Image.ContentMode getContentModeFromString(String contentMode) {
        switch (contentMode) {
            case "stretch": return Image.ContentMode.Stretch;
            case "tile": return Image.ContentMode.Tile;
            case "fill": return Image.ContentMode.Fill;
            case "fit": return Image.ContentMode.Fit;
            default: return Image.ContentMode.Original;
        }
    }

    private Screen.ActionBarButtons getBarButtons(String string) {
        switch (string) {
            case "close": return Screen.ActionBarButtons.Close;
            case "back": return Screen.ActionBarButtons.Back;
            case "both": return Screen.ActionBarButtons.Both;
            default: return Screen.ActionBarButtons.None;
        }
    }
}
