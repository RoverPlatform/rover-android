package io.rover;

import android.content.Context;
import android.content.Intent;
import android.telecom.Call;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.internal.ParcelableGeofence;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ata_n on 2016-04-04.
 */
public class EventSubmitTask implements Runnable, JsonApiCompletionHandler {

    public interface Callback {
        void onReceivedMessages(List<Message> messages);
        void onReceivedGeofences(List geofences);
        //void onSuccess(Event event);
    }

    private Event mEvent;
    private Context mContext;
    private Callback mCallback;

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public EventSubmitTask(Context context, Event event) {
        mEvent = event;
        mContext = context;
    }

    @Override
    public void run() {
        try {
            NetworkTask networkTask = new NetworkTask("POST", new URL("https://rover-content-api-development.herokuapp.com/v1/events"));

            JsonApiObjectSerializer serializer = new ObjectSerializer(mEvent, mContext);
            JsonPayloadProvider payloadProvider = new JsonApiPayloadProvider(serializer);

            networkTask.setPayloadProvider(payloadProvider);

            JsonApiObjectMapper mapper = new ObjectMapper(); // TODO: could get this from Rover singleton?
            JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
            responseHandler.setCompletionHandler(this);

            networkTask.setResponseHandler(responseHandler);

            networkTask.run();

        } catch (MalformedURLException e) {

        }
    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {
        if (mCallback == null) {
            return;
        }

        ArrayList<ParcelableGeofence> geofences = new ArrayList<ParcelableGeofence>();
        ArrayList<Message> messages = new ArrayList<Message>();

        for (Object object : includedObject) {
            if (object instanceof ParcelableGeofence) {
                geofences.add((ParcelableGeofence)object);
            } else if (object instanceof Message) {
                messages.add((Message)object);
            }
        }

        if (geofences.size() > 0) {
            mCallback.onReceivedGeofences(geofences);
        }

        if (messages.size() > 0) {
            mCallback.onReceivedMessages(messages);
        }
    }
}