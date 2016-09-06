package io.rover;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import io.rover.model.Screen;
import io.rover.network.JsonApiResponseHandler;
import io.rover.network.JsonResponseHandler;
import io.rover.network.NetworkTask;
import io.rover.ui.ScreenFragment;

/**
 * Created by ata_n on 2016-07-11.
 */
public class RemoteScreenActivity extends AppCompatActivity {

    public static String INTENT_EXTRA_SCREEN = "INTENT_EXTRA_SCREEN";

    private FetchLandingPageTask mFetchTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        layout.setId(View.generateViewId());

        setContentView(layout);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {

        Fragment screenFragment = ScreenFragment.newInstance((Screen) bundle.getParcelable(INTENT_EXTRA_SCREEN));


        getSupportFragmentManager()
                .beginTransaction()
                .add(layout.getId(), screenFragment, ScreenFragment.TAG)
                .commit();



        }

        Uri data = getIntent().getData();
        if (data != null) {
            String messageId = data.getPath();
            if (messageId != null) {
                mFetchTask = new FetchLandingPageTask();
                mFetchTask.execute(messageId);

            }
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);


    }

    private class FetchLandingPageTask extends AsyncTask<String, Void, Screen> implements JsonResponseHandler.JsonCompletionHandler {

        private ObjectMapper mObjectMapper;
        private Screen screen = null;

        @Override
        protected Screen doInBackground(String... params) {
            String messageId = params[0];
            if (messageId == null) {
                return null;
            }

            mObjectMapper = new ObjectMapper();

            JsonResponseHandler responseHandler = new JsonResponseHandler();
            responseHandler.setCompletionHandler(this);

            NetworkTask networkTask = Router.getLandingPageNetworkTask(messageId);
            networkTask.setResponseHandler(responseHandler);

            networkTask.run();

            return screen;
        }


        @Override
        public void onReceivedJSONObject(JSONObject jsonObject) {
            screen = (Screen) mObjectMapper.getObject("screens", null, jsonObject);
        }

        @Override
        public void onReceivedJSONArray(JSONArray jsonArray) {}

        @Override
        protected void onPostExecute(Screen screen) {
            if (screen == null) { return; }

            ScreenFragment screenFragment = (ScreenFragment) getSupportFragmentManager().findFragmentByTag(ScreenFragment.TAG);
            if (screenFragment == null) {
                return;
            }

            screenFragment.setScreen(screen);
            screenFragment.getAdapter().notifyDataSetChanged();
        }
    }
}
