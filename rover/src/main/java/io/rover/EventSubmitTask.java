package io.rover;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.rover.model.Device;
import io.rover.model.Event;
import io.rover.model.GeofenceRegion;
import io.rover.network.HttpResponse;
import io.rover.network.JsonApiPayloadProvider;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiPayloadProvider.JsonApiObjectSerializer;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.NetworkTask;

/**
 * Created by Rover Labs Inc on 2016-04-04.
 */
public class EventSubmitTask implements Runnable, JsonApiResponseHandler.JsonApiCompletionHandler {

    public interface Callback {
        void onReceivedGeofences(List<GeofenceRegion> geofences);
        void onEventRegistered(Event event);
    }

    private Event mEvent;
    private Context mContext;
    private Callback mCallback;
    private static final String TAG = "Rover:EventSubmitTask";

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public EventSubmitTask(Context context, Event event) {
        mEvent = event;
        mContext = context;
    }

    @Override
    public void run() {

        if (mContext == null) {
            Log.w(TAG, "Cannot submit event context is null");
            return;
        }

        Log.d(TAG, "Submitting: " + mEvent.getClass());

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

        Device device = Device.getInstance();

        if (!device.hasCheckedForAdTracking()) {
            Log.d("EventSubmitTask", "Getting advertising id");
            AdvertisingIdTask advertisingIdTask = new AdvertisingIdTask(mContext);
            AdvertisingIdClient.Info info = advertisingIdTask.execute();

            device.setCheckedForAdTracking(true);

            if (info != null) {
                device.setAdTrackingEnabled(true);
                device.setAdvertisingId(info.getId());
            } else {
                device.setAdTrackingEnabled(false);
            }

        }

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

        ArrayList<GeofenceRegion> geofenceRegions = new ArrayList<>();

        for (Object object : includedObject) {
            if (object instanceof GeofenceRegion) {
                geofenceRegions.add((GeofenceRegion) object);
            }
        }

        if (geofenceRegions.size() > 0) {
            mCallback.onReceivedGeofences(geofenceRegions);
        } else {
            Log.i(TAG, "No geofences in event submission result, so not changing currently monitored geofences.");
        }
    }
}