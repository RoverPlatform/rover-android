package io.rover;

import android.os.Build;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by ata_n on 2016-03-31.
 */
public class Device {
    private static Device mInstance;

    private Device() {}

    public static Device getInstance() {
        if (mInstance == null) {
            mInstance = new Device();
        }
        return mInstance;
    }

    public String getIdentifier() {
        return "DEVICE_ID";
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

    public boolean getLocalNotificationsEnabled() {
        return true;
    }

    public boolean getRemoteNotificationsEnabled() {
        return true;
    }

    public boolean getBluetoothEnabled() {
        return true;
    }

    public String getToken() {
        return "TOKEN";
    }

    public String getAdvertisingIdentifier() {
        return "AID";
    }

    public boolean getLocationMonitoringEnabled() {
        return true;
    }

    public boolean getBackgroundEnabled() {
        return true;
    }

    public String getCarrier() {
        return "Rogers";
    }

    public String getAppIdentifier() {
        return "APP ID";
    }

    public String getLocaleRegion() {
        return Locale.getDefault().getISO3Country();
    }

    public String getLocaleLanguage() {
        return Locale.getDefault().getLanguage();
    }
}