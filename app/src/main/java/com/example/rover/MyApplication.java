package com.example.rover;

import android.app.Application;
import android.location.Location;

import java.util.Date;

import io.rover.Rover;
import io.rover.RoverConfig;
import io.rover.model.GimbalPlaceTransitionEvent;
import io.rover.model.LocationUpdateEvent;

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
