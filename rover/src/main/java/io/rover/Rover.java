package io.rover;

import android.Manifest;
import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.gcm.GcmListenerService;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.iid.InstanceIDListenerService;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.rover.model.BeaconConfiguration;
import io.rover.model.BeaconTransitionEvent;
import io.rover.model.Device;
import io.rover.model.DeviceUpdateEvent;
import io.rover.model.Event;
import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.Location;
import io.rover.model.LocationUpdateEvent;

/**
 * Created by ata_n on 2016-03-21.
 */
public class Rover implements EventSubmitTask.Callback {

    protected static Rover mSharedInstance = new Rover();

    private Context mApplicationContext;
    private PendingIntent mLocationPendingIntent;
    private PendingIntent mGeofencePendingIntent;
    private PendingIntent mNearbyMessagesPendingIntent;
    private PendingIntent mAppLaunchPendingIntent;
    private ExecutorService mEventExecutorService = Executors.newSingleThreadExecutor();
    private ArrayList<RoverObserver> mObservers = new ArrayList<>();
    private String mProjectNumber;

    private Rover() {}

    public static void setup(Context context, RoverConfig config) {
        mSharedInstance.mApplicationContext = context;
        mSharedInstance.mProjectNumber = config.mProjectNum;
        Router.setApiKey(config.mAppToken);
        Router.setDeviceId(Device.getInstance().getIdentifier(context));
    }

    public static void startMonitoring() {

        final LocationRequest locationRequest = new LocationRequest()
                .setInterval(60000)
                .setFastestInterval(60000)
                .setSmallestDisplacement(0)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        GoogleApiConnection connection = new GoogleApiConnection(mSharedInstance.mApplicationContext);
        connection.setCallbacks(new GoogleApiConnection.Callbacks() {
            @Override
            public int onConnected(final GoogleApiClient client) {
                // Location Updates
                if (ContextCompat.checkSelfPermission(mSharedInstance.mApplicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, mSharedInstance.getLocationPendingIntent());
                }

                // Nearyby Messages
                Nearby.Messages.subscribe(client, mSharedInstance.getNearbyMessagesPendingIntent(), mSharedInstance.getNearbySubscriptionOptions())
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status status) {
                                if (status.isSuccess()) {
                                    Log.i("Nearby", "Subscribed successfully.");
                                } else {
                                    Log.i("Nearby", "Could not subscribe." + status.getStatusMessage());
                                    //handleUnsuccessfulNearbyResult(status);
                                }

                                client.disconnect();
                            }
                        });

                return GoogleApiConnection.KEEP_ALIVE;
            }
        });
        connection.connect();
    }

    public static void stopMonitoring() {
        GoogleApiConnection connection = new GoogleApiConnection(mSharedInstance.mApplicationContext);
        connection.setCallbacks(new GoogleApiConnection.Callbacks() {
            @Override
            public int onConnected(final GoogleApiClient client) {
                // Location Updates
                LocationServices.FusedLocationApi.removeLocationUpdates(client, mSharedInstance.getLocationPendingIntent());

                // Nearby Messages
                Nearby.Messages.unsubscribe(client, mSharedInstance.getNearbyMessagesPendingIntent())
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status status) {
                                if (status.isSuccess()) {
                                    Log.i("Nearby", "Unsubscribed successfuly.");
                                } else {
                                    Log.i("Nearby", "Could not unsubscribe");
                                }

                                client.disconnect();
                            }
                        });

                return GoogleApiConnection.KEEP_ALIVE;
            }
        });
        connection.connect();
    }

    public static void addObserver(RoverObserver observer) {
        mSharedInstance.mObservers.add(observer);
    }

    public static void deleteObserver(RoverObserver observer) {
        mSharedInstance.mObservers.remove(observer);
    }

    public static void activityStarted(Activity activity) {

    }

    public static void activityStopped(Activity activity) {

    }

    public interface OnInboxReloadListener {
        void onSuccess(List<io.rover.model.Message> messages);
        void onFailure();
    }

    public static void reloadInbox(final OnInboxReloadListener listener) {
        FetchInboxTask task = new FetchInboxTask();
        task.setCallback(new FetchInboxTask.Callback() {
            @Override
            public void onSuccess(List<io.rover.model.Message> messages) {
                if (listener != null) {
                    listener.onSuccess(messages);
                }
            }
        });
        task.execute();
    }

    private PendingIntent getLocationPendingIntent() {
        if (mLocationPendingIntent == null) {
            Intent intent = new Intent(mApplicationContext, LocationUpdateService.class);
            mLocationPendingIntent = PendingIntent.getService(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mLocationPendingIntent;
    }

    private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent == null) {
            Intent intent = new Intent(mApplicationContext, GeofenceTransitionService.class);
            mGeofencePendingIntent = PendingIntent.getService(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mGeofencePendingIntent;
    }

    private PendingIntent getNearbyMessagesPendingIntent() {
        if (mNearbyMessagesPendingIntent == null) {
            Intent intent = new Intent(mApplicationContext, NearbyMessageService.class);
            mNearbyMessagesPendingIntent = PendingIntent.getService(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mNearbyMessagesPendingIntent;
    }

    private PendingIntent getAppLaunchPendingIntent() {
        // TODO: intent needs to contain message id
        if (mAppLaunchPendingIntent == null) {
            String packageName = mApplicationContext.getPackageName();
            Intent intent = mApplicationContext.getPackageManager().getLaunchIntentForPackage(packageName);
            mAppLaunchPendingIntent = PendingIntent.getActivity(mApplicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return mAppLaunchPendingIntent;
    }

    private SubscribeOptions getNearbySubscriptionOptions() {
        return new SubscribeOptions.Builder()
                .setStrategy(Strategy.BLE_ONLY)
                .setCallback(new SubscribeCallback() {
                    @Override
                    public void onExpired() {
                        Log.i("NearbySubscribe", "No longer subscribing.");
                    }
                })
                .build();
    }

    private void deliverMessage(io.rover.model.Message message) {
        // TODO: call observer

        for (RoverObserver observer : mObservers) {
            if (observer instanceof RoverObserver.MessageDeliveryObserver) {
                ((RoverObserver.MessageDeliveryObserver) observer).shouldDeliverMessage(message);
            }
        }
        
        // TODO: need to add the message ID to the intent

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                .setSmallIcon(R.drawable.rover_notification_icon)
                .setContentTitle(message.getTitle())
                .setContentText(message.getText())
                .setContentIntent(getAppLaunchPendingIntent());

        NotificationManager manager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(message.getId(), 12345 /* Rover notification id */, builder.build());

        // TODO: call observer

        for (RoverObserver observer : mObservers) {
            if (observer instanceof RoverObserver.MessageDeliveryObserver) {
                ((RoverObserver.MessageDeliveryObserver) observer).onDeliveredMessage(message);
            }
        }

    }

    protected void sendEvent(Event event) {
        EventSubmitTask eventTask = new EventSubmitTask(mApplicationContext, event);
        eventTask.setCallback(this);
        // TODO: this may have to be synchronous
        mEventExecutorService.execute(eventTask);
    }

    @Override
    public void onEventRegistered(Event event) {
        if (event instanceof GeofenceTransitionEvent) {
            GeofenceTransitionEvent gtEvent = (GeofenceTransitionEvent)event;
            Location location = gtEvent.getLocation();

            for (RoverObserver observer : mObservers) {
                if (observer instanceof RoverObserver.GeofenceTransitionObserver) {
                    switch (gtEvent.getGeofenceTransition()) {
                        case Geofence.GEOFENCE_TRANSITION_ENTER:
                            ((RoverObserver.GeofenceTransitionObserver) observer).onEnterGeofence(location);
                            break;
                        case Geofence.GEOFENCE_TRANSITION_EXIT:
                            ((RoverObserver.GeofenceTransitionObserver) observer).onExitGeofence(location);
                            break;
                    }
                }
            }
        } else if (event instanceof BeaconTransitionEvent) {
            BeaconTransitionEvent btEvent = (BeaconTransitionEvent)event;
            BeaconConfiguration bc = (BeaconConfiguration)btEvent.getBeaconConfiguration();

            for (RoverObserver observer : mObservers) {
                if (observer instanceof RoverObserver.BeaconTransitionObserver) {
                    switch (btEvent.getTransition()) {
                        case BeaconTransitionEvent.TRANSITION_ENTER:
                            ((RoverObserver.BeaconTransitionObserver) observer).onEnterBeaconRegion(bc);
                            break;
                        case BeaconTransitionEvent.TRANSITION_EXIT:
                            ((RoverObserver.BeaconTransitionObserver) observer).onExitBeaconRegion(bc);
                            break;
                    }
                }
            }
        }
    }

    @Override
    public void onReceivedGeofences(final List geofences) {
        GoogleApiConnection connection = new GoogleApiConnection(mApplicationContext);
        connection.setCallbacks(new GoogleApiConnection.Callbacks() {
            @Override
            public int onConnected(final GoogleApiClient client) {
                LocationServices.GeofencingApi.removeGeofences(client, getGeofencePendingIntent())
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status status) {
                                if (status.isSuccess()) {
                                    addGeofences(client);
                                } else {
                                    client.disconnect();
                                }
                            }
                        });

                return GoogleApiConnection.KEEP_ALIVE;
            }

            public void addGeofences(final GoogleApiClient client) {
                GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
                builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
                builder.addGeofences(geofences);

                GeofencingRequest request = builder.build();

                if (ContextCompat.checkSelfPermission(mSharedInstance.mApplicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    LocationServices.GeofencingApi.addGeofences(client, request, getGeofencePendingIntent())
                            .setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status status) {
                                    if (status.isSuccess()) {
                                        // TODO: Clean up

                                        for (RoverObserver observer : mObservers) {
                                            if (observer instanceof RoverObserver.GeofenceRegistrationObserver) {
                                                ((RoverObserver.GeofenceRegistrationObserver) observer).onRegisteredGeofences(geofences);
                                            }
                                        }
                                    }
                                    client.disconnect();
                                }
                            });
                } else {
                    client.disconnect();
                }
            }
        });
        connection.connect();
    }

    @Override
    public void onReceivedMessages(List<io.rover.model.Message> messages) {
        for (io.rover.model.Message message : messages) {
            deliverMessage(message);
        }
    }

    static public void simulateGeofenceEnter(String id) {
        Event event = new GeofenceTransitionEvent(id, Geofence.GEOFENCE_TRANSITION_ENTER, new Date());
        mSharedInstance.sendEvent(event);
    }

    static public void simulateGeofenceExit(String id) {
        Event event = new GeofenceTransitionEvent(id, Geofence.GEOFENCE_TRANSITION_EXIT, new Date());
        mSharedInstance.sendEvent(event);
    }

    static public void registerForNotifications() {
        Intent intent = new Intent(mSharedInstance.mApplicationContext, GcmRegistrationService.class);
        mSharedInstance.mApplicationContext.startService(intent);
    }

    static public class LocationUpdateService extends IntentService {

        public LocationUpdateService() { super("LocationUpdateService"); }

        @Override
        protected void onHandleIntent(Intent intent) {
            LocationResult result = LocationResult.extractResult(intent);
            if (result != null) {
                Event event = new LocationUpdateEvent(result.getLastLocation(), new Date());
                mSharedInstance.sendEvent(event);
            }
        }
    }

    static public class GeofenceTransitionService extends IntentService {

        public GeofenceTransitionService() { super("GeofenceTransitionService"); }

        @Override
        protected void onHandleIntent(Intent intent) {
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);

            if (geofencingEvent.hasError()) {
               //String errorMessage = GeofenceErrorMessage.getErrorString(this,
                //        geofencingEvent.getErrorCode());
                Log.e("GeofenceService", "SOME ERROR");
                return;
            }

            // Get the transition type.
            int geofenceTransition = geofencingEvent.getGeofenceTransition();

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            Date date = new Date();

            for (Geofence geofence: triggeringGeofences) {
                Event event = new GeofenceTransitionEvent(geofence.getRequestId(), geofenceTransition, date);
                mSharedInstance.sendEvent(event);
            }
        }
    }

    static public class NearbyMessageService extends IntentService {

        public NearbyMessageService() { super("NearbyMessageService"); }

        @Override
        protected void onHandleIntent(Intent intent) {
            Nearby.Messages.handleIntent(intent, new MessageListener() {
                @Override
                public void onFound(Message message) {
                    handleMessage(BeaconTransitionEvent.TRANSITION_ENTER, message);
                }

                @Override
                public void onLost(Message message) {
                    handleMessage(BeaconTransitionEvent.TRANSITION_EXIT, message);
                }

                private void handleMessage(int transition, Message message) {
                    String type = message.getType();
                    if (!type.equals("rover-configuration-id")){
                        return;
                    }

                    String messageString = new String(message.getContent());

                    Log.i("NearbyMessage", "Message string: " + messageString);
                    Log.i("NearbyMessage", "Message namespaced type: " + message.getNamespace() +
                            "/" + message.getType());
                    Event event = new BeaconTransitionEvent(transition, messageString, new Date());
                    mSharedInstance.sendEvent(event);
                }
            });
        }
    }

    static public class GcmRegistrationService extends IntentService {

        public GcmRegistrationService() { super("GcmRegistrationService"); }

        @Override
        protected void onHandleIntent(Intent intent) {
            try {
                InstanceID instanceID = InstanceID.getInstance(this);
                String token = instanceID.getToken(mSharedInstance.mProjectNumber, GoogleCloudMessaging.INSTANCE_ID_SCOPE);

                Device.setGcmToken(token, mSharedInstance.mApplicationContext);

                Event event = new DeviceUpdateEvent(new Date());
                mSharedInstance.sendEvent(event);
            } catch (Exception e) {
                Log.e("GcmRegistrationService", "Failed to get token.");
                e.printStackTrace();
            }
        }
    }

    static public class RoverInstanceIDListenerService extends InstanceIDListenerService {
        @Override
        public void onTokenRefresh() {
            Intent intent = new Intent(this, GcmRegistrationService.class);
            startService(intent);
        }
    }

    static public class RoverGcmListenerService extends GcmListenerService {
        @Override
        public void onMessageReceived(String from, Bundle data) {

            String attributesString = data.getString("attributes");
            String typeString = data.getString("type");
            String idString = data.getString("id");

            if (attributesString == null || typeString == null || idString == null || !typeString.equals("messages")) {
                Log.e("RoverGcmListener", "Bad data bundle for Gcm message");
                return;
            }

            JSONObject attributes = null;

            try {
                attributes = new JSONObject(data.getString("attributes"));
            } catch (JSONException e) {
                Log.e("RoverGcmListener","Bad JSON for message attributes");
            }

            ObjectMapper mapper = new ObjectMapper();
            io.rover.model.Message message = (io.rover.model.Message)mapper.getObject(typeString, idString, attributes);

            mSharedInstance.deliverMessage(message);
        }
    }
}
