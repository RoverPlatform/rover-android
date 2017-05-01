package io.rover;

import android.content.Context;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;

/**
 * Created by Rover Labs Inc on 2016-04-18.
 */
public class AdvertisingIdTask {

    private Context mContext;

    public AdvertisingIdTask(Context context) {
        mContext = context;
    }

    public AdvertisingIdClient.Info execute() {
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
