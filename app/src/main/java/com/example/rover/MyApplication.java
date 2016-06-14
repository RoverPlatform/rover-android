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
                .setApplicationToken("c554c131e1c2959a22c8147eceeb0e7d")
                .setProjectNumber("951983123918")
                .build();

        Rover.setup(this.getApplicationContext(), config);
    }
}
