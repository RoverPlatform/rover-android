package io.rover;

import android.content.Context;
import android.os.AsyncTask;

import io.rover.model.Message;
import io.rover.network.HttpResponse;
import io.rover.network.JsonApiPayloadProvider;
import io.rover.network.NetworkTask;

/**
 * Created by Rover Labs Inc on 2016-11-29.
 */

public class PatchMessageTask extends AsyncTask<Message, Void, Boolean> {

    public interface Callback {
        void onSuccess();
        void onFailure();
    }

    private Context mContext;
    private Callback mCallback;

    public PatchMessageTask(Context context) {
        mContext = context;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    protected Boolean doInBackground(Message... messages) {
        
        if (messages.length == 0)
            return false;

        if (mContext == null)
            return false;

        Message message = messages[0];


        NetworkTask networkTask = Router.getPatchMessageNetworkTask(message.getId());

        if (networkTask == null) {
            return false;
        }

        JsonApiPayloadProvider.JsonApiObjectSerializer serializer = new ObjectSerializer(message, mContext);
        NetworkTask.PayloadProvider payloadProvider = new JsonApiPayloadProvider(serializer);

        networkTask.setPayloadProvider(payloadProvider);

        HttpResponse response = networkTask.run();

        if (response != null) {
            response.close();
        }

        return response != null && response.isSuccessful();
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (mCallback != null) {
            if (success) {
                mCallback.onSuccess();
            } else {
                mCallback.onFailure();
            }
        }
    }
}
