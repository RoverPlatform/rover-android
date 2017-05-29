package io.rover;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;

/**
 * Created by Rover Labs Inc on 2016-04-18.
 */
public class AdvertisingIdTask {

    private static final String TAG = "Rover:AdvertisingIdTask";

    private Context mContext;

    public AdvertisingIdTask(Context context) {
        mContext = context;
    }

    public AdvertisingIdClient.Info execute() {

        if (mContext == null) {
            Log.w(TAG, "Unable to grab device's advertising id");
            return null;
        }

        try {
            return AdvertisingIdClient.getAdvertisingIdInfo(mContext);
        } catch (IllegalStateException | GooglePlayServicesNotAvailableException | IOException | GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // Ignore
            // Bug with google's internal implementation
        }

        return null;
    }

}
