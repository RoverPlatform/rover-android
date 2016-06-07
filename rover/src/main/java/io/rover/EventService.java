package io.rover;

import android.app.IntentService;
import android.content.Intent;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import io.rover.model.Event;
import io.rover.network.NetworkTask;
import io.rover.network.NetworkTask.JsonPayloadProvider;
import io.rover.network.JsonApiPayloadProvider;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.JsonApiPayloadProvider.JsonApiObjectSerializer;
import io.rover.network.JsonApiResponseHandler.JsonApiCompletionHandler;

/**
 * Created by ata_n on 2016-03-23.
 */
public class EventService extends IntentService implements JsonApiCompletionHandler {

    private static String TAG = "EventService";

    public EventService() {
        super("EventService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        //String applicationToken = Rover.getApplicationToken();

        final Event event = intent.getParcelableExtra("event");



        try {
            NetworkTask networkTask = new NetworkTask("POST", new URL("http://sdfd.com"));

            JsonApiObjectSerializer serializer = new ObjectSerializer(event, getApplicationContext());
            JsonPayloadProvider payloadProvider = new JsonApiPayloadProvider(serializer);

            networkTask.setPayloadProvider(payloadProvider);

            JsonApiObjectMapper mapper = new ObjectMapper(); // TODO: could get this from Rover singleton?
            JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
            responseHandler.setCompletionHandler(this);

            networkTask.setResponseHandler(responseHandler);

        } catch (MalformedURLException e) {

        }
    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {

//        // Geofences
//
//        ArrayList<ParcelableGeofence> geofences = new ArrayList<ParcelableGeofence>();
//        for (Object object : includedObject) {
//            if (object instanceof ParcelableGeofence) { geofences.add((ParcelableGeofence)object); }
//        }
//
//        // start service that monitors for geofences
//        Intent intent = new Intent(getApplicationContext(), GoogleApiService.class);
//        intent.putParcelableArrayListExtra("geofences", geofences);
//
//        startService(intent);
//
//        // Messages
    }
}
