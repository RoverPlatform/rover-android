package io.rover;

import android.os.AsyncTask;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import io.rover.model.Message;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.NetworkTask;

/**
 * Created by ata_n on 2016-04-07.
 */
public class FetchInboxTask extends AsyncTask<Void, Void, Void> implements JsonApiResponseHandler.JsonApiCompletionHandler {

    private Callback mCallback;
    private List<Message> mInbox;

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public interface Callback {
        void onSuccess(List<Message> inbox);
    }

    @Override
    protected Void doInBackground(Void... params) {

        NetworkTask networkTask = Router.getInboxNetworkTask();

        if (networkTask == null) {
            return null;
        }

        JsonApiObjectMapper mapper = new ObjectMapper();
        JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
        responseHandler.setCompletionHandler(this);

        networkTask.setResponseHandler(responseHandler);

        networkTask.run();

        return null;
    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {
        if (response instanceof List) {
            mInbox = (List)response;
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (mCallback != null) {
            if (mInbox != null) {
                mCallback.onSuccess(mInbox);
            } else {
                //mCallback.onFailure(error);
            }
        }
    }
}
