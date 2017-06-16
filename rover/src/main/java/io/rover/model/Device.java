package io.rover.model;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by Roverlabs Inc. on 2016-03-31.
 */
public class Device {
    private static Device mInstance;
    private static String SHARED_DEVICE = "ROVER_SHARED_DEVICE";
    private static final String TAG = "Rover:Device";

    private String mUDID;
    private String mGcmToken;
    private String mAdvertisingId;
    private boolean mAdTrackingEnabled;
    private boolean mCheckedForAdTracking;
    private boolean mGimbalMode;

    private Device() {
        mCheckedForAdTracking = false;
    }

    public static Device getInstance() {
        if (mInstance == null) {
            mInstance = new Device();
        }
        return mInstance;
    }

    public String getIdentifier(Context context) {
        if (mUDID != null) {
            return mUDID;
        }

        if (context == null) {
            /*
                if context is null there is no sensible default.
                We should never get here unless a function call to Rover was called before initialization and it slipped through
                initialization checks.
                Instead use a temporary uuid in memory. This means a new device is created on each launch of the app if Rover was not initialized
            */
            mUDID = UUID.randomUUID().toString();
            return mUDID;
        }

        SharedPreferences sharedData = context.getSharedPreferences(SHARED_DEVICE, 0);
        mUDID = sharedData.getString("UDID", null);

        if (mUDID == null) {
            mUDID = UUID.randomUUID().toString();

            SharedPreferences.Editor editor = sharedData.edit();
            editor.putString("UDID", mUDID);
            editor.apply();
        }

        return mUDID;
    }

    public String getOSVersion() {
        return Build.VERSION.RELEASE;
    }

    public String getManufacturer() {
        return Build.MANUFACTURER;
    }

    public String getModel() {
        return Build.MODEL;
    }

    public String getTimeZone() {
        return TimeZone.getDefault().getID();
    }

    public boolean getBluetoothEnabled(Context context) {
        //if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                return true;
            }
        //}
        return false;
    }

    public boolean areNotificationsEnabled(Context context) {
        if (context == null) {
            Log.w(TAG, "Unable to get device's notification status");
            return false;
        }

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        return manager.areNotificationsEnabled();
    }

    public String getGcmToken() {
        if (mGcmToken != null) {
            return mGcmToken;
        }

        try {
            mGcmToken = FirebaseInstanceId.getInstance().getToken();
        } catch (Exception e) {
            Log.w(TAG, "Unable to get device's push token");
        }

        return mGcmToken;
    }

    public void setGcmToken(String token) {
       mGcmToken = token;
    }

    public void setCheckedForAdTracking(boolean checked) {
        mCheckedForAdTracking = checked;
    }

    public String getAdvertisingIdentifier() {
        return mAdvertisingId;
    }

    public boolean getAdTrackingEnabled() { return mAdTrackingEnabled; }

    public boolean hasCheckedForAdTracking() { return  mCheckedForAdTracking; }

    public void setAdvertisingId(String id) { mAdvertisingId = id; }

    public void setAdTrackingEnabled(boolean enabled) { mAdTrackingEnabled = enabled; }

    public boolean getLocationMonitoringEnabled() {
        return true;
    }

    public boolean getBackgroundEnabled() {
        return true;
    }

    public String getCarrier(Context context) {
        TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        return manager.getNetworkOperatorName();
    }

    public String getAppIdentifier(Context context) {
        return context.getPackageName();
    }

    public String getLocaleRegion() {
        try {
            return Locale.getDefault().getISO3Country();
        } catch (MissingResourceException e) {
            return Locale.getDefault().getCountry();
        }
    }

    public String getLocaleLanguage() {
        return Locale.getDefault().getLanguage();
    }

    public boolean isGimbalMode() { return mGimbalMode; }

    public void setGimbalMode(boolean gimbalMode) { mGimbalMode = gimbalMode; }

    // TODO: add getNotificationsEnabled()
}