package io.rover;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.AnimRes;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import io.rover.model.Action;
import io.rover.model.Block;
import io.rover.model.BlockPressEvent;
import io.rover.model.Experience;
import io.rover.model.ExperienceDismissEvent;
import io.rover.model.ExperienceLaunchEvent;
import io.rover.model.Screen;
import io.rover.model.ScreenViewEvent;
import io.rover.network.HttpResponse;
import io.rover.network.JsonResponseHandler;
import io.rover.network.NetworkTask;
import io.rover.ui.AssetManager;
import io.rover.ui.ExperienceScreenAnimation;
import io.rover.ui.ScreenFragment;

/**
 * Created by Rover Labs Inc on 2016-08-15.
 */
public class ExperienceActivity extends AppCompatActivity implements ScreenFragment.OnBlockListener {
    
    private static String TAG = "ExperienceActivity";


    private RelativeLayout mLayout;
    private FetchExperienceTask mFetchTask;
    private Experience mExperience;
    private String mSessionId;
    private boolean mHasPresentedFirstScreen = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        layout.setId(R.id.screen_layout);

        mLayout = layout;

        setContentView(layout);


        Uri data = getIntent().getData();
        if (data != null) {
            String experienceId = data.getPath();
            if (experienceId != null) {
                mFetchTask = new FetchExperienceTask();
                mFetchTask.execute(experienceId);
            }
        }

        mSessionId = UUID.randomUUID().toString();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        /*
            Clean up the image memory cache. We don't want to hold onto bitmaps when we aren't displaying the experience
         */
        AssetManager manager = AssetManager.getSharedAssetManager(getApplicationContext());
        manager.flushMemoryCache();

        if (isFinishing() && mExperience != null) {
            Rover.submitEvent(new ExperienceDismissEvent(mExperience, mSessionId, new Date()));

            for (RoverObserver observer : Rover.mSharedInstance.mObservers) {
                if (observer instanceof RoverObserver.ExperienceObserver) {
                    ((RoverObserver.ExperienceObserver) observer).onExperienceDismiss(
                            mExperience,
                            mSessionId
                    );
                }
            }
        }
    }

    public void setExperience(Experience experience) {
        mExperience = experience;

        Rover.submitEvent(new ExperienceLaunchEvent(mExperience, mSessionId, new Date()));

        for (RoverObserver observer : Rover.mSharedInstance.mObservers) {
            if (observer instanceof RoverObserver.ExperienceObserver) {
                ((RoverObserver.ExperienceObserver) observer).onExperienceLaunch(
                        mExperience,
                        mSessionId
                );
            }

            if (observer instanceof RoverObserver.ExtendedExperienceObserver ) {
                ((RoverObserver.ExtendedExperienceObserver) observer).onExperienceLaunch(
                        this,
                        mExperience,
                        mSessionId
                );
            }
        }

        Screen homeScreen = mExperience.getHomeScreen();

        presentNextScreen(homeScreen);
    }

    public void presentNextScreen(Screen screen) {
        if (screen == null) {
            return;
        }

        Fragment screenFragment = ScreenFragment.newInstance(screen);

        presentNextScreen(screenFragment, screen, new ExperienceScreenAnimation());
    }

    public void presentNextScreen(Fragment screenFragment, Screen screen, ExperienceScreenAnimation animation) {
        presentNextScreen(screenFragment, screen, animation, null, null);
    }

    public void presentNextScreen(Fragment screenFragment, Screen screen, ExperienceScreenAnimation animation, Screen fromScreen, Block fromBlock) {
        if (screenFragment == null) {
            return;
        }

        for (RoverObserver observer : Rover.mSharedInstance.mObservers) {
            if (observer instanceof RoverObserver.ExtendedExperienceObserver) {
                screenFragment = ((RoverObserver.ExtendedExperienceObserver) observer).willPresentScreen(this, screenFragment, screen);
            }
        }

        if (screenFragment == null) {
            return;
        }



        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(mLayout.getId(), screenFragment, "SCREEN");


        if (animation != null) {
            transaction.setCustomAnimations(animation.getEnter(), animation.getExit(), animation.getPopEnter(), animation.getPopExit());
        }

        if (mHasPresentedFirstScreen) {
            transaction.addToBackStack(null);
        }

        transaction.commitAllowingStateLoss();

        if (!mHasPresentedFirstScreen) {
            mHasPresentedFirstScreen = true;
        }

        if (screenFragment instanceof ScreenFragment) {
            trackScreenView(screenFragment, screen, fromScreen, fromBlock);
        }
    }

    public void popCurrentScreen() {
        getSupportFragmentManager()
                .popBackStack();
    }

    @Override
    public void onBlockClick(Fragment screenFragment, Screen screen, Block block) {
        Action action = block.getAction();

        if (action == null) {
            return;
        }

        Rover.submitEvent(new BlockPressEvent(block, screen, mExperience, mSessionId, new Date()));

        for (RoverObserver observer : Rover.mSharedInstance.mObservers) {
            if (observer instanceof RoverObserver.ExperienceObserver) {
                ((RoverObserver.ExperienceObserver) observer).onBlockClick(
                        block,
                        screen,
                        mExperience,
                        mSessionId
                );
            }

            if (observer instanceof RoverObserver.ExtendedExperienceObserver) {
                ((RoverObserver.ExtendedExperienceObserver) observer).onBlockClick(
                        this,
                        screenFragment,
                        screen,
                        block,
                        mSessionId
                );
            }
        }

        switch (action.getType()) {
            case Action.GOTO_SCREEN_ACTION: {
                String screenId = action.getUrl();
                Screen newScreen = mExperience.getScreen(screenId);
                presentNextScreen(newScreen);
                break;
            }
            default: {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(action.getUrl()));
                startActivity(intent);
                break;
            }
        }


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getSupportFragmentManager().popBackStack();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }


    public static Intent createIntent(Context context, String id) {
        Uri uri = new Uri.Builder().scheme("rover")
                .authority("experience")
                .appendPath(id).build();

        Intent intent = new Intent(context, ExperienceActivity.class);
        intent.setData(uri);

        return intent;
    }

    private void trackScreenView(Fragment screenFragment, Screen screen, Screen fromScreen, Block fromBlock) {
        Rover.submitEvent(new ScreenViewEvent(screen, mExperience, fromScreen, fromBlock, mSessionId, new Date()));

        for (RoverObserver observer : Rover.mSharedInstance.mObservers) {
            if (observer instanceof RoverObserver.ExperienceObserver) {
                ((RoverObserver.ExperienceObserver) observer).onScreenView(
                        screen,
                        mExperience,
                        fromScreen,
                        fromBlock,
                        mSessionId
                );
            }

            if (observer instanceof RoverObserver.ExtendedExperienceObserver) {
                ((RoverObserver.ExtendedExperienceObserver) observer).onScreenView(
                        this,
                        screenFragment,
                        mExperience,
                        screen,
                        fromScreen,
                        fromBlock,
                        mSessionId
                );
            }
        }
    }


    private class FetchExperienceTask extends AsyncTask<String, Void, Experience> implements JsonResponseHandler.JsonCompletionHandler {

        private ObjectMapper mObjectMapper;
        private Experience experience = null;

        @Override
        protected Experience doInBackground(String... params) {
            String experienceId = params[0];
            if (experienceId == null) {
                return null;
            }

            mObjectMapper = new ObjectMapper();

            JsonResponseHandler responseHandler = new JsonResponseHandler();
            responseHandler.setCompletionHandler(this);

            NetworkTask networkTask = Router.getExperienceNetworkTask(experienceId);
            HttpResponse response = networkTask.run();

            if (response != null) {
                try {
                    responseHandler.onHandleResponse(response);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    response.close();
                }
            }

            return experience;
        }


        @Override
        public void onReceivedJSONObject(JSONObject jsonObject) {
            try {
                JSONObject data = jsonObject.getJSONObject("data");
                experience = (Experience) mObjectMapper.getObject("experiences", data.getString("id"),
                        data.getJSONObject("attributes"));
            } catch (JSONException e) {
                Log.e("ExperienceActivity", "Error downloading experience");
            }
        }

        @Override
        public void onReceivedJSONArray(JSONArray jsonArray) {}

        @Override
        protected void onPostExecute(Experience experience) {
            if (experience == null) { return; }

            setExperience(experience);
        }
    }
}
