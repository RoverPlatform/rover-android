package io.rover;

import android.content.Context;

import com.google.android.gms.location.internal.ParcelableGeofence;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import io.rover.model.Device;
import io.rover.model.Event;
import io.rover.model.Message;
import io.rover.network.JsonApiPayloadProvider;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiPayloadProvider.JsonApiObjectSerializer;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.NetworkTask;
import io.rover.network.NetworkTask.JsonPayloadProvider;

/**
 * Created by ata_n on 2016-04-04.
 */
public class EventSubmitTask implements Runnable, JsonApiResponseHandler.JsonApiCompletionHandler {

    public interface Callback {
        void onReceivedMessages(List<Message> messages);
        void onReceivedGeofences(List geofences);
        void onEventRegistered(Event event);
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

        NetworkTask networkTask = Router.getEventsNetworkTask();

        if (networkTask == null) {
            return;
        }

        JsonApiObjectSerializer serializer = new ObjectSerializer(mEvent, mContext);
        JsonPayloadProvider payloadProvider = new JsonApiPayloadProvider(serializer);

        networkTask.setPayloadProvider(payloadProvider);

        JsonApiObjectMapper mapper = new ObjectMapper();
        JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
        responseHandler.setCompletionHandler(this);

        networkTask.setResponseHandler(responseHandler);

        // TODO:
//      AdvertisingIdTask advertisingIdTask = new AdvertisingIdTask(mContext);
//       advertisingIdTask.setCallback(new AdvertisingIdTask.Callback() {
//                @Override
//                public void onFinished(String advertisingId, boolean isLAT) {
//                    Device.getInstance().setAdvertisingId(advertisingId);
//                    Device.getInstance().setAdTrackingEnabled(!isLAT);
//
//                    networkTask.run();
//                }
//            });
//            advertisingIdTask.onPostExecute(advertisingIdTask.doInBackground());


        networkTask.run();


    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {
        if (mCallback == null) {
            return;
        }

        if (response instanceof Event) {
            mCallback.onEventRegistered((Event)response);
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