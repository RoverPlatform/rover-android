package io.rover.network;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by ata_n on 2016-07-13.
 */
public class JsonResponseHandler implements NetworkTask.ResponseHandler {

    public interface JsonCompletionHandler {
        void onReceivedJSONObject(JSONObject jsonObject);
        void onReceivedJSONArray(JSONArray jsonArray);
    }

    private JsonCompletionHandler mComletionHandler;

    public void setCompletionHandler(JsonCompletionHandler handler) {
        mComletionHandler = handler;
    }

    @Override
    public void onHandleResponse(InputStreamReader reader) throws IOException {
        JsonReader jsonReader = new JsonReader(reader);
        JsonToken token = jsonReader.peek();

        switch (token) {
            case BEGIN_OBJECT:
                JSONObject jsonObject = readJSONObject(jsonReader);
                if (mComletionHandler != null) {
                    mComletionHandler.onReceivedJSONObject(jsonObject);
                }
                break;
            case BEGIN_ARRAY:
                JSONArray jsonArray = readJSONArray(jsonReader);
                if (mComletionHandler != null) {
                    mComletionHandler.onReceivedJSONArray(jsonArray);
                }
                break;
            default:
                break;
        }
    }

    public JSONObject readJSONObject(JsonReader reader) throws IOException {
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
                Log.w("ResponseHandler", "Could not read value for key: `" + name + "`. Possibly a bad number value.  - Will skip");
                reader.skipValue();
            }
        }
        reader.endObject();

        return object;
    }

    public JSONArray readJSONArray(JsonReader reader) throws IOException {
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
                        Log.w("ResponseHandler", "Incorrect number value - will skip");
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
