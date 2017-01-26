package io.rover;

import android.content.Context;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;

import io.rover.model.Device;

/**
 * Created by ata_n on 2016-04-18.
 */
public class AdvertisingIdTask {

    private Context mContext;

    public AdvertisingIdTask(Context context) {
        mContext = context;
    }

    public AdvertisingIdClient.Info execute() {
        try {
            AdvertisingIdClient.Info info = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
            return info;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesNotAvailableException e) {
            e.printStackTrace();
        } catch (GooglePlayServicesRepairableException e) {
            e.printStackTrace();
        }

        return null;
    }

}
