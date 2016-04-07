package io.rover;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ata_n on 2016-04-07.
 */
public class FetchInboxTask implements Runnable, JsonApiCompletionHandler {

    private Callback mCallback;

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public interface Callback {
        void onReceivedMessages(List<Message> messages);
    }

    @Override
    public void run() {
        try {
            NetworkTask networkTask = new NetworkTask("GET", new URL("https://rover-content-api-development.herokuapp.com/v1/events"));

            JsonApiObjectMapper mapper = new ObjectMapper(); // TODO: could get this from Rover singleton?
            JsonApiResponseHandler responseHandler = new JsonApiResponseHandler(mapper);
            responseHandler.setCompletionHandler(this);

            networkTask.setResponseHandler(responseHandler);

            networkTask.run();
        } catch (MalformedURLException e) {

        }

    }

    @Override
    public void onHandleCompletion(Object response, List includedObject) {
        if (mCallback == null) {
            return;
        }

        if (response instanceof List) {
            List<Message> messages = (List)response;
            mCallback.onReceivedMessages(messages);
        }
    }
}
