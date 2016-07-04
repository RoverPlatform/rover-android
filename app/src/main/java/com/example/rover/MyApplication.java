package com.example.rover;

import android.app.Application;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.net.Uri;

import io.rover.NotificationProvider;
import io.rover.Rover;
import io.rover.RoverConfig;
import io.rover.model.Message;

/**
 * Created by ata_n on 2016-03-21.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RoverConfig config = new RoverConfig.Builder()
                .setApplicationToken("b43963962ea03fc2f4b456a5cbe49b40")
                .setNotificationProvider(new NotificationProvider() {
                    @Override
                    public PendingIntent getNotificationPendingIntent(Message message) {
                        return null;
                    }

                    @Override
                    public int getSmallIconForNotification(Message message) {
                        return 0;
                    }

                    @Override
                    public Bitmap getLargeIconForNotification(Message message) {
                        return null;
                    }

                    @Override
                    public Uri getSoundForNotification(Message message) {
                        return null;
                    }
                })
                .build();

        Rover.setup(this, config);
    }
}
