package com.example.rover;

import android.app.Application;

import io.rover.Rover;
import io.rover.RoverConfig;

/**
 * Created by ata_n on 2016-03-21.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RoverConfig config = new RoverConfig.Builder()
                .setApplicationToken("b43963962ea03fc2f4b456a5cbe49b40")
                .build();

        Rover.setup(this, config);
    }
}
