package io.rover;

import android.support.annotation.NonNull;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ata_n on 2016-03-31.
 */
class JsonApiResponseHandler implements JsonResponseHandler {

    private JsonApiObjectMapper mMapper;
    private JsonApiCompletionHandler mCompletionHandler;

    public JsonApiResponseHandler(JsonApiObjectMapper mapper) {
        mMapper = mapper;
    }

    public JsonApiCompletionHandler getCompletionHandler() { return mCompletionHandler; }
    public void setCompletionHandler(JsonApiCompletionHandler handler) {
        mCompletionHandler = handler;
    }

    @Override
    public void onHandleResponse(JsonReader reader) throws IOException {

        Object response = null;
        ArrayList<Object> includedObjects = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {

            String name = reader.nextName();

            if (name.equals("data")) {

                JsonToken token = reader.peek();

                switch (token) {
                    case BEGIN_OBJECT:
                        response = readObject(reader);
                        break;
                    case BEGIN_ARRAY:
                        response = readArray(reader);
                        break;
                    default:
                        break;
                }

            } else if (name.equals("included")) {

                reader.beginArray();
                while (reader.hasNext()) {

                    Object obj = readObject(reader);

                    if (obj != null) {
                        includedObjects.add(obj);
                    }
                }
                reader.endArray();

            }
        }
        reader.endObject();

        if (mCompletionHandler != null) {
            mCompletionHandler.onHandleCompletion(response, includedObjects);
        }
    }

    private Object readObject(JsonReader reader) throws IOException {
        String type = null;
        String id = null;
        JSONObject attributes = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            if (name.equals("id")) {
                id = reader.nextString();
            } else if (name.equals("type")) {
                type = reader.nextString();
            } else if (name.equals("attributes")) {
                attributes = readJSONObject(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return mMapper.getObject(type, id, attributes);
    }

    private ArrayList readArray(JsonReader reader) throws IOException {
        ArrayList arrayList = new ArrayList();

        reader.beginArray();
        while (reader.hasNext()) {
            Object object = readObject(reader);

            if (object != null) {
                arrayList.add(object);
            }
        }
        reader.endArray();

        return arrayList;
    }

    private JSONObject readJSONObject(JsonReader reader) throws IOException {
        JSONObject object = new JSONObject();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            JsonToken token = reader.peek();

            try {
                switch (token) {
                    case STRING:
                        object.put(name, reader.nextString());
                        break;
                    case NUMBER:
                        object.put(name, reader.nextDouble());
                        break;
                    case BOOLEAN:
                        object.put(name, reader.nextBoolean());
                        break;
                    case NULL:
                        object.put(name, JSONObject.NULL);
                        reader.skipValue();
                        break;
                    case BEGIN_OBJECT:
                        object.put(name, readJSONObject(reader));
                        break;
                    case BEGIN_ARRAY:
                        object.put(name, readJSONArray(reader));
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            } catch (JSONException e) {
                Log.w("JsonApiResponseHandler", "Could not read value for key: `" + name + "`. Possibly a bad number value.  - Will skip");
                reader.skipValue();
            }
        }
        reader.endObject();

        return object;
    }

    private JSONArray readJSONArray(JsonReader reader) throws IOException {
        JSONArray array = new JSONArray();

        reader.beginArray();
        while (reader.hasNext()) {
            JsonToken token = reader.peek();

            switch (token) {
                case STRING:
                    array.put(reader.nextString());
                    break;
                case NUMBER:
                    try {
                        array.put(reader.nextDouble());
                    } catch (JSONException e) {
                        Log.w("JsonApiResponseHandler", "Incorrect number value - will skip");
                        reader.skipValue();
                    }
                    break;
                case BOOLEAN:
                    array.put(reader.nextBoolean());
                    break;
                case NULL:
                    array.put(JSONObject.NULL);
                    reader.skipValue();
                    break;
                case BEGIN_ARRAY:
                    array.put(readJSONArray(reader));
                    break;
                case BEGIN_OBJECT:
                    array.put(readJSONObject(reader));
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endArray();

        return array;
    }
}

interface JsonApiObjectMapper {
    Object getObject(String type, String identifier, JSONObject attributes);
}

interface JsonApiCompletionHandler {
    void onHandleCompletion(Object response, List includedObject);
}