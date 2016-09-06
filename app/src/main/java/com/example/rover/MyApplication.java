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
                .setApplicationToken("6c546189dc45df1293bddc18c0b54786")
                .build();

        Rover.setup(this, config);

    }
}
