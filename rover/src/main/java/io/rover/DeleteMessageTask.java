package io.rover;

import android.os.AsyncTask;

import java.io.IOException;
import java.util.List;

import io.rover.network.HttpResponse;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.NetworkTask;

/**
 * Created by chrisrecalis on 2016-11-21.
 */

class DeleteMessageTask extends AsyncTask<String, Void, Boolean> {

    public interface Callback {
        void onComplete();
        void onFailure();
    }

    private Callback mCallback;

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    protected Boolean doInBackground(String... messageIds) {

        if (messageIds.length == 0) {
            return null;
        }

        String messageId = messageIds[0];

        NetworkTask networkTask = Router.deleteMessageNetworkTask(messageId);

        if (networkTask == null) {
            return null;
        }

        HttpResponse response = networkTask.run();

        return response != null && response.isSuccessful();
    }

    @Override
    protected void onPostExecute(Boolean success) {
        if (mCallback != null) {
            if (success) {
                mCallback.onComplete();
            } else {
                mCallback.onFailure();
            }
        }
    }
}
