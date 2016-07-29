package io.rover.model;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.TelephonyManager;

import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by ata_n on 2016-03-31.
 */
public class Device {
    private static Device mInstance;
    private static String SHARED_DEVICE = "ROVER_SHARED_DEVICE";

    private String mUDID;
    private String mGcmToken;
    private String mAdvertisingId;
    private boolean mAdTrackingEnabled;
    private boolean mGimbalMode;

    private Device() {}

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

    public String getGcmToken(Context context) {
        if (mGcmToken != null) {
            return mGcmToken;
        }

        SharedPreferences sharedData = context.getSharedPreferences(SHARED_DEVICE, 0);
        mGcmToken = sharedData.getString("GCM_TOKEN", null);

        return mGcmToken;
    }

    public static void setGcmToken(String token, Context context) {
        Device.getInstance().mGcmToken = token;

        SharedPreferences sharedData = context.getSharedPreferences(SHARED_DEVICE, 0);
        SharedPreferences.Editor editor = sharedData.edit();
        editor.putString("GCM_TOKEN", token);
        editor.apply();
    }

    public String getAdvertisingIdentifier() {
        return mAdvertisingId;
    }

    public boolean getAdTrackingEnabled() { return mAdTrackingEnabled; }

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
        return Locale.getDefault().getISO3Country();
    }

    public String getLocaleLanguage() {
        return Locale.getDefault().getLanguage();
    }

    public boolean isGimbalMode() { return mGimbalMode; }

    public void setGimbalMode(boolean gimbalMode) { mGimbalMode = gimbalMode; }

    // TODO: add getNotificationsEnabled()
}