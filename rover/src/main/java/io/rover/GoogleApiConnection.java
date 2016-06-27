package io.rover;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyPermissions;

/**
 * Created by ata_n on 2016-04-04.
 */
public class GoogleApiConnection implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static int KEEP_ALIVE = 1;
    public static int DISCONNECT = 0;

    public interface Callbacks {
        int onConnected(GoogleApiClient client);
    }

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;
    private Callbacks mCallbacks;

    public void setCallbacks(Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public GoogleApiConnection(Context context) {
        mContext = context;

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Nearby.MESSAGES_API, new MessagesOptions.Builder()
                    .setPermissions(NearbyPermissions.BLE)
                    .build())
                .build();
    }

    public void connect() {
        mGoogleApiClient.connect();
    }

    public static boolean checkPlayServices(Context context) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                // TODO: pass back to developer
                Log.e("GoogleApiConnection", "GooglePlayServices not installed or out of date");
            } else {
                Log.e("GoogleApiConnection", "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (mCallbacks != null) {
            if (mCallbacks.onConnected(mGoogleApiClient) == DISCONNECT) {
                mGoogleApiClient.disconnect();
            }
        } else {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("GoogleApiConnection", "Connection suspended: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("GoogleApiConnection", "Connection failed: " + connectionResult.getErrorMessage());
    }
}
