package com.example.rover;

import android.app.Activity;
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
                .setApplicationToken("37638bb00e6ba35eb7b4bbdd6586f00e")
                .build();

        Rover.setup(this, config);

    }
}
