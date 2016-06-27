package io.rover;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.location.Geofence;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;

import io.rover.model.BeaconTransitionEvent;
import io.rover.model.Customer;
import io.rover.model.Device;
import io.rover.model.DeviceUpdateEvent;
import io.rover.model.Event;
import io.rover.model.GeofenceTransitionEvent;
import io.rover.model.LocationUpdateEvent;
import io.rover.model.Message;
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
            }

        } else if (mObject instanceof Customer) {
            Customer customer = (Customer)mObject;

            jsonObject.put("identifier", customer.getIdentifier());
            jsonObject.put("name", customer.getName());
            jsonObject.put("email", customer.getEmail());
            jsonObject.put("phone-number", customer.getPhoneNumber());
            //jsonObject.put("gender", customer.getGender());
            //jsonObject.put("age", customer.getAge());
            //jsonObject.put("tags", new JSONArray(customer.getTags()));

        } else if (mObject instanceof Device) {
            Device device = (Device)mObject;

            jsonObject.put("os-name", "Android");
            jsonObject.put("platform", "Android");
            jsonObject.put("sdk-version", "4.0.0");
            jsonObject.put("development", true);
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
}