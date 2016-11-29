package com.example.rover;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.net.Uri;

import io.rover.NotificationProvider;
import io.rover.Rover;
import io.rover.RoverConfig;
import io.rover.Traits;
import io.rover.model.Message;

/**
 * Created by ata_n on 2016-03-21.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();


        RoverConfig config = new RoverConfig.Builder()
                .setApplicationToken("6c546189dc45df1293bddc18c0b54786")
                .build();

        Rover.setup(this, config);

        Rover.identify(new Traits().putFirstName("Android").putIdentifier("Nexus6"));

    }
}
