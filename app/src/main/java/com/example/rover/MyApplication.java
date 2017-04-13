package com.example.rover;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import io.rover.NotificationProvider;
import io.rover.Rover;
import io.rover.RoverConfig;
import io.rover.RoverObserver;
import io.rover.Traits;
import io.rover.model.Message;

/**
 * Created by Roverlabs Inc. on 2016-03-21.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();


        RoverConfig config = new RoverConfig.Builder()
                .setApplicationToken("API_TOKEN_HERE")
                .setExperienceActivity(MyCustomExperience.class)
                .build();

        Rover.setup(this, config);

        Rover.addObserver(new RoverObserver.NotificationInteractionObserver() {
            @Override
            public void onNotificationOpened(Message message) {
                if (!message.isRead()) {
                    message.setRead(true);
                    Rover.patchMessage(message, null);
                }
            }

            @Override
            public void onNotificationDeleted(Message message) {
                Rover.deleteMessage(message, null);
            }
        });
    }
}
