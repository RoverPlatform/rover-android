package io.rover;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ata_n on 2016-03-21.
 */
public class Rover implements EventSubmitTask.Callback {

    private static Rover mSharedInstance = new Rover();

    private Context mApplicationContext;
    private String mApplicationToken;
    private PendingIntent mLocationPendingIntent;
    private PendingIntent mGeofencePendingIntent;
    private PendingIntent mNearbyMessagesPendingIntent;
    private ExecutorService mEventExecutorService = Executors.newSingleThreadExecutor();
    private ArrayList<RoverObserver> mObservers = new ArrayList<>();

    private Rover() {}

    public static void setup(Context context, String applicationToken) {
        mSharedInstance.mApplicationContext = context;
        mSharedInstance.mApplicationToken = applicationToken;
    }

    public static void startMonitoring() {

        final LocationRequest locationRequest = new LocationRequest()
                .setInterval(5000)
                .setFastestInterval(5000)
                .setSmallestDisplacement(0)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        GoogleApiConnection connection = new GoogleApiConnection(mSharedInstance.mApplicationContext);
        connection.setCallbacks(new GoogleApiConnection.Callbacks() {
            @Override
            public int onConnected(GoogleApiClient client) {
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
                                    Log.i("Nearby", "Could not subscribe.");
                                    //handleUnsuccessfulNearbyResult(status);
                                }
                            }
                        });

                return GoogleApiConnection.DISCONNECT;
            }
        });
        connection.connect();
    }

    public static void stopMonitoring() {
        GoogleApiConnection connection = new GoogleApiConnection(mSharedInstance.mApplicationContext);
        connection.setCallbacks(new GoogleApiConnection.Callbacks() {
            @Override
            public int onConnected(GoogleApiClient client) {
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
                            }
                        });

                return GoogleApiConnection.DISCONNECT;
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

    public interface OnInboxReloadListener {
        void onSuccess(List<io.rover.Message> messages);
        void onFailure();
    }

    public static void reloadInbox(final OnInboxReloadListener listener) {
        FetchInboxTask task = new FetchInboxTask();
        task.setCallback(new FetchInboxTask.Callback() {
            @Override
            public void onReceivedMessages(List<io.rover.Message> messages) {
                if (listener != null) {
                    listener.onSuccess(messages);
                }
            }
        });
        // TODO: use some other executor maybe?
        mSharedInstance.mEventExecutorService.execute(task);
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

    protected static String getApplicationToken() {
        return mSharedInstance.mApplicationToken;
    }

    protected void sendEvent(Event event) {
        EventSubmitTask eventTask = new EventSubmitTask(mApplicationContext, event);
        eventTask.setCallback(this);
        // TODO: this may have to be synchronous
        mEventExecutorService.execute(eventTask);
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
                                }
                                client.disconnect();
                            }
                        });

                return GoogleApiConnection.KEEP_ALIVE;
            }

            public void addGeofences(GoogleApiClient client) {
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

                                    }
                                }
                            });
                }
            }
        });
        connection.connect();
    }

    @Override
    public void onReceivedMessages(List<io.rover.Message> messages) {
        for (io.rover.Message message : messages) {
            // call observer

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext)
                    //.setSmallIcon(/*R.drawable.*/)
                    .setContentTitle(message.getTitle())
                    .setContentText(message.getText());

            NotificationManager manager = (NotificationManager) mApplicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(message.getId(), 12345 /* Rover notification id */, builder.build());

            // call observer
        }
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
}
