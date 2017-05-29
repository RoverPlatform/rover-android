package io.rover;

import android.os.AsyncTask;
import java.util.List;

import io.rover.model.Message;
import io.rover.network.HttpResponse;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonApiResponseHandler.JsonApiObjectMapper;
import io.rover.network.NetworkTask;

/**
 * Created by Roverlabs Inc. on 2016-04-07.
 */
public class FetchInboxTask extends AsyncTask<Void, Void, Boolean> implements JsonApiResponseHandler.JsonApiCompletionHandler {

    private Callback mCallback;
    private List<Message> mInbox;
    private String mErrorMessage = "";

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public interface Callback {
        void onSuccess(List<Message> inbox);
        void onFailure(String errorMessage);
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        NetworkTask networkTask = Router.getInboxNetworkTask();

        if (networkTask == null) {
            return null;
        }

        JsonApiObjectMapper mapper = new ObjectMapper();
        JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
        responseHandler.setCompletionHandler(this);

        HttpResponse response = networkTask.run();

        if (response != null && response.isSuccessful()) {
            try {
                responseHandler.onHandleResponse(response);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                response.close();
            }
        } else {

            if (networkTask.hasTaskFailed()) {
                mErrorMessage = networkTask.getTaskFailureMessage();
            } else if(response != null) {
                mErrorMessage = "Status: " + response.getStatus();
                mErrorMessage += " Body: ";
                mErrorMessage += ((response.getBody() == null) ? "" : response.getBody().toString());
            }

            return false;
        }
    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {
        if (response instanceof List) {
            mInbox = (List)response;
        }
    }

    @Override
    protected void onPostExecute(Boolean successful) {
        if (mCallback != null) {
            if (successful) {
                mCallback.onSuccess(mInbox);
            } else {
                mCallback.onFailure(mErrorMessage);
            }
        }
    }
}
