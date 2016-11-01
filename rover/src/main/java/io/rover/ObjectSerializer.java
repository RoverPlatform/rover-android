package io.rover;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.location.Geofence;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import io.rover.model.Action;
import io.rover.model.BeaconTransitionEvent;
import io.rover.model.BlockPressEvent;
import io.rover.model.Customer;
import io.rover.model.Device;
import io.rover.model.DeviceUpdateEvent;
import io.rover.model.Event;
import io.rover.model.ExperienceDismissEvent;
import io.rover.model.ExperienceLaunchEvent;
import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.GimbalPlaceTransitionEvent;
import io.rover.model.LocationUpdateEvent;
import io.rover.model.Message;
import io.rover.model.ScreenViewEvent;
import io.rover.network.JsonApiPayloadProvider;

/**
 * Created by ata_n on 2016-03-31.
 */
public class ObjectSerializer implements JsonApiPayloadProvider.JsonApiObjectSerializer {

    private Object mObject;
    private Context mApplicationContext;

    public ObjectSerializer(Object object, Context context) {
        mObject = object;
        mApplicationContext = context;
    }

    @Override
    public String getType() {
        if (mObject instanceof Event) {
            return "events";
        } else if (mObject instanceof Message) {
            return "messages";
        }
        return null;
    }

    @Override
    public String getIdentifier() {
        if (mObject instanceof Message) {
            return ((Message) mObject).getId();
        }
        return null;
    }

    @Override
    public JSONObject getAttributes() {
        JSONObject jsonObject = new JSONObject();

        try {
            putAttributes(jsonObject);
        } catch (JSONException e) {
            Log.e("ObjectSerializer", "Error creating attributes JSON");
            e.printStackTrace();
        }

        return jsonObject;
    }

    private void putAttributes(JSONObject jsonObject) throws JSONException {

        if (mObject instanceof Event) {
            Event event = (Event)mObject;

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            jsonObject.put("time", sdf.format(event.getDate()));

            Customer customer = Customer.getInstance(mApplicationContext);
            ObjectSerializer customerSerializer = new ObjectSerializer(customer, mApplicationContext);

            jsonObject.put("user", customerSerializer.getAttributes());

            Device device = Device.getInstance();
            ObjectSerializer deviceSerializer = new ObjectSerializer(device, mApplicationContext);

            jsonObject.put("device", deviceSerializer.getAttributes());

            if (event instanceof LocationUpdateEvent) {
                LocationUpdateEvent luEvent = (LocationUpdateEvent)event;

                jsonObject.put("object", "location");
                jsonObject.put("action", "update");
                jsonObject.put("latitude", luEvent.getLocation().getLatitude());
                jsonObject.put("longitude", luEvent.getLocation().getLongitude());
                jsonObject.put("accuracy", luEvent.getLocation().getAccuracy());
            } else if (event instanceof GeofenceTransitionEvent) {
                GeofenceTransitionEvent gtEvent = (GeofenceTransitionEvent) event;

                jsonObject.put("object", "geofence-region");
                jsonObject.put("action", gtEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT ? "exit" : "enter");
                jsonObject.put("identifier", gtEvent.getGeofenceId());
            } else if (event instanceof BeaconTransitionEvent) {
                BeaconTransitionEvent btEvent = (BeaconTransitionEvent) event;

                jsonObject.put("object", "beacon-region");
                jsonObject.put("action", btEvent.getTransition() == BeaconTransitionEvent.TRANSITION_EXIT ? "exit" : "enter");
                jsonObject.put("configuration-id", btEvent.getId());
            } else if (event instanceof DeviceUpdateEvent) {
                DeviceUpdateEvent duEvent = (DeviceUpdateEvent)event;

                jsonObject.put("object", "device");
                jsonObject.put("action", "update");
            } else if (event instanceof GimbalPlaceTransitionEvent) {
                GimbalPlaceTransitionEvent gmblEvent = (GimbalPlaceTransitionEvent)event;

                jsonObject.put("object", "gimbal-place");
                jsonObject.put("action", gmblEvent.getGimbalPlaceTransition() == GimbalPlaceTransitionEvent.TRANSITION_EXIT ? "exit" : "enter");
                jsonObject.put("gimbal-place-id", gmblEvent.getPlaceId());
            } else if (event instanceof ExperienceLaunchEvent) {
                ExperienceLaunchEvent expEvent = (ExperienceLaunchEvent)event;

                jsonObject.put("object", "experience");
                jsonObject.put("action", "launched");
                jsonObject.put("experience-id", expEvent.getExperience().getId());
                jsonObject.put("version-id", expEvent.getExperience().getVersion());
                jsonObject.put("experience-session-id", expEvent.getSessionId());
            } else if (event instanceof ExperienceDismissEvent) {
                ExperienceDismissEvent expEvent = (ExperienceDismissEvent)event;

                jsonObject.put("object", "experience");
                jsonObject.put("action", "dismissed");
                jsonObject.put("experience-id", expEvent.getExperience().getId());
                jsonObject.put("version-id", expEvent.getExperience().getVersion());
                jsonObject.put("experience-session-id", expEvent.getSessionId());
            } else if (event instanceof ScreenViewEvent) {
                ScreenViewEvent expEvent = (ScreenViewEvent)event;

                jsonObject.put("object", "experience");
                jsonObject.put("action", "screen-viewed");
                jsonObject.put("experience-id", expEvent.getExperience().getId());
                jsonObject.put("version-id", expEvent.getExperience().getVersion());
                jsonObject.put("experience-session-id", expEvent.getSessionId());
                if (expEvent.getScreen() != null) {
                    jsonObject.put("screen-id", expEvent.getScreen().getId());
                }
                if (expEvent.getFromScreen() != null) {
                    jsonObject.put("from-screen-id", expEvent.getFromScreen().getId());
                }
                if (expEvent.getFromBlock() != null) {
                    jsonObject.put("from-block-id", expEvent.getFromBlock().getId());
                }
            } else if (event instanceof BlockPressEvent) {
                BlockPressEvent expEvent = (BlockPressEvent) event;

                jsonObject.put("object", "experience");
                jsonObject.put("action", "block-clicked");
                jsonObject.put("version-id", expEvent.getExperience().getVersion());
                jsonObject.put("experience-session-id", expEvent.getSessionId());
                if (expEvent.getBlock() != null) {
                    jsonObject.put("block-id", expEvent.getBlock().getId());
                }
                if (expEvent.getExperience() != null) {
                    jsonObject.put("experience-id", expEvent.getExperience().getId());
                }
                if (expEvent.getBlock() != null) {
                    jsonObject.put("block-action", getActionJSON(expEvent.getBlock().getAction()));
                }
                if (expEvent.getScreen() != null) {
                    jsonObject.put("screen-id", expEvent.getScreen().getId());
                }
            }

        } else if (mObject instanceof Customer) {
            Customer customer = (Customer)mObject;
            if (customer.getIdentifier().hasBeenSet()) {
                jsonObject.put("identifier", customer.getIdentifier().getOrElse(null));
            }

            if (customer.getFirstName().hasBeenSet()) {
                jsonObject.put("first-name", customer.getFirstName().getOrElse(null));
            }

            if (customer.getLastName().hasBeenSet()) {
                jsonObject.put("last-name", customer.getLastName().getOrElse(null));
            }

            if (customer.getEmail().hasBeenSet()) {
                jsonObject.put("email", customer.getEmail().getOrElse(null));
            }

            if (customer.getPhoneNumber().hasBeenSet()) {
                jsonObject.put("phone-number", customer.getPhoneNumber().getOrElse(null));
            }

            if (customer.getGender().hasBeenSet()) {
                jsonObject.put("gender", customer.getGender().getOrElse(null));
            }

            if (customer.getAge().hasBeenSet()) {
                jsonObject.put("age", customer.getAge().getOrElse(null));
            }

            if (customer.getTags().hasBeenSet()) {
                jsonObject.put("tags", new JSONArray(Arrays.asList(customer.getTags().getOrElse(new String[0]))));
            }

        } else if (mObject instanceof Device) {
            Device device = (Device)mObject;

            jsonObject.put("os-name", "Android");
            jsonObject.put("platform", "Android");
            jsonObject.put("sdk-version", Rover.VERSION);
            jsonObject.put("udid", device.getIdentifier(mApplicationContext));
            jsonObject.put("locale-lang", device.getLocaleLanguage());
            jsonObject.put("locale-region", device.getLocaleRegion());
            jsonObject.put("os-version", device.getOSVersion());
            jsonObject.put("manufacturer", device.getManufacturer());
            jsonObject.put("model", device.getModel());
            jsonObject.put("time-zone", device.getTimeZone());
            jsonObject.put("bluetooth-enabled", device.getBluetoothEnabled(mApplicationContext));
            jsonObject.put("token", device.getGcmToken(mApplicationContext));
            jsonObject.put("aid", device.getAdvertisingIdentifier());
            jsonObject.put("ad-tracking", device.getAdTrackingEnabled());
            jsonObject.put("location-monitoring-enabled", device.getLocationMonitoringEnabled());
            jsonObject.put("background-enabled", device.getBackgroundEnabled());
            jsonObject.put("carrier", device.getCarrier(mApplicationContext));
            jsonObject.put("app-identifier", device.getAppIdentifier(mApplicationContext));


        } else if (mObject instanceof Message) {
            Message message = (Message)mObject;

            jsonObject.put("read", message.isRead());
        }
    }

    private JSONObject getActionJSON(Action action) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        switch (action.getType()) {
            case Action.DEEPLINK_ACTION: {
                jsonObject.put("type", "open-url");
                jsonObject.put("url", action.getUrl());
                break;
            }
            case Action.GOTO_SCREEN_ACTION: {
                jsonObject.put("type", "go-to-screen");
                jsonObject.put("screen-id", action.getUrl());
                break;
            }
        }

        return jsonObject;
    }
}