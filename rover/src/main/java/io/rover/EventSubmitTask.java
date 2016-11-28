package io.rover;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.location.internal.ParcelableGeofence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.rover.model.Event;
import io.rover.network.HttpResponse;
import io.rover.network.JsonApiPayloadProvider;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiPayloadProvider.JsonApiObjectSerializer;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.NetworkTask;

/**
 * Created by ata_n on 2016-04-04.
 */
public class EventSubmitTask implements Runnable, JsonApiResponseHandler.JsonApiCompletionHandler {

    public interface Callback {
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

        Log.d("EventSubmitTask", "Submitting: " + mEvent.getClass());

        NetworkTask networkTask = Router.getEventsNetworkTask();

        if (networkTask == null) {
            return;
        }

        JsonApiObjectSerializer serializer = new ObjectSerializer(mEvent, mContext);
        NetworkTask.PayloadProvider payloadProvider = new JsonApiPayloadProvider(serializer);

        networkTask.setPayloadProvider(payloadProvider);

        JsonApiObjectMapper mapper = new ObjectMapper();
        JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
        responseHandler.setCompletionHandler(this);

        HttpResponse response = networkTask.run();

        if (response != null) {
            try {
                responseHandler.onHandleResponse(response);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                response.close();
            }


        }

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

        for (Object object : includedObject) {
            if (object instanceof ParcelableGeofence) {
                geofences.add((ParcelableGeofence)object);
            }
        }

        if (geofences.size() > 0) {
            mCallback.onReceivedGeofences(geofences);
        }


    }
}