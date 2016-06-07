package io.rover;

import android.content.Context;
import android.os.AsyncTask;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
//import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;

/**
 * Created by ata_n on 2016-04-18.
 */
public class AdvertisingIdTask extends AsyncTask<Void, Void, Void> {

    private Context mContext;
    private Callback mCallback;

    static private String id;
    static private boolean isLAT;

    public interface Callback {
        void onFinished(String advertisingId, boolean isLAT);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public AdvertisingIdTask(Context context) {
        super();
        mContext = context;
    }

    @Override
    protected Void doInBackground(Void... params) {

        if (id != null) {
            // We already have the Id
            return null;
        }

        AdvertisingIdClient.Info adInfo = null;
        try {
            adInfo = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
        } catch (IOException e) {
            // Unrecoverable error connecting to Google Play services (e.g.,
            // the old version of the service doesn't support getting AdvertisingId).

        } catch (GooglePlayServicesNotAvailableException e) {
            // Google Play services is not available entirely.
        } catch (GooglePlayServicesRepairableException e) {
            // Encountered a recoverable error connecting to Google Play services.
        }
        id = adInfo.getId();
        isLAT = adInfo.isLimitAdTrackingEnabled();

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (mCallback == null) {
            return;
        }

        mCallback.onFinished(id, isLAT);
    }
}
