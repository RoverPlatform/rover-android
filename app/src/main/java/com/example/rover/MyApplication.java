package com.example.rover;


import android.app.Application;

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
                .setApplicationToken("029631d85c585c3df152f914685b4d32")
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
