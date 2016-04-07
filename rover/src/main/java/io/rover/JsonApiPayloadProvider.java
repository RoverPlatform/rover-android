package io.rover;

import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

/**
 * Created by ata_n on 2016-03-31.
 */
class JsonApiPayloadProvider implements JsonPayloadProvider {

    private JsonApiObjectSerializer mSerializer;

    public JsonApiPayloadProvider(JsonApiObjectSerializer serializer) {
        mSerializer = serializer;
    }

    @Override
    public void onProvidePayload(JsonWriter writer) throws IOException {
        writer.beginObject();
        {
            writer.name("data");
            writer.beginObject();
            {
                String identifier = mSerializer.getIdentifier();
                if (identifier != null) {
                    writer.name("id").value(identifier);
                }
                writer.name("type").value(mSerializer.getType());
                writer.name("attributes");
                JSONObject attributes = mSerializer.getAttributes();
                writeJSONObject(attributes, writer);
            }
            writer.endObject();
        }
        writer.endObject();
    }

    private void writeJSONObject(JSONObject object, JsonWriter writer) throws IOException {
        writer.beginObject();

        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = null;

            try {
                value = object.get(key);
            } catch (JSONException e) {
                Log.e("JsonApiPayloadProvider", "Bad key for object: " + key);
                e.printStackTrace();
            }

            if (value instanceof Number) {
                writer.name(key).value((Number)value);
            } else if (value instanceof Boolean) {
                writer.name(key).value((Boolean)value);
            } else if (value instanceof String) {
                writer.name(key).value((String)value);
            } else if (value == JSONObject.NULL) {
                writer.name(key).nullValue();
            } else if (value instanceof JSONObject) {
                writer.name(key);
                writeJSONObject((JSONObject)value, writer);
            } else if (value instanceof JSONArray) {
                writer.name(key);
                writeJSONArray((JSONArray)value, writer);
            }
        }

        writer.endObject();
    }

    private void writeJSONArray(JSONArray array, JsonWriter writer) throws IOException {
        writer.beginArray();

        for (int i = 0; i < array.length(); i++) {
            Object value = null;

            try {
                value = array.get(i);
            } catch (JSONException e) {
                Log.e("JsonApiPayloadProvider", "Bad index for array: " + i);
                e.printStackTrace();
            }

            if (value instanceof Number) {
                writer.value((Number) value);
            } else if (value instanceof Boolean) {
                writer.value((Boolean)value);
            } else if (value instanceof String) {
                writer.value((String)value);
            } else if (value == JSONObject.NULL) {
                writer.nullValue();
            } else if (value instanceof JSONObject) {
                writeJSONObject((JSONObject)value, writer);
            } else if (value instanceof JSONArray) {
                writeJSONArray((JSONArray)value, writer);
            }
        }

        writer.endArray();
    }
}

interface JsonApiObjectSerializer {
    String getIdentifier();
    String getType();
    JSONObject getAttributes();
}
