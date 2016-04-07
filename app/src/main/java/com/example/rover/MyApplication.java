package com.example.rover;

import android.app.Application;

import io.rover.Rover;

/**
 * Created by ata_n on 2016-03-21.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Rover.setup(this.getApplicationContext(), "0628d761f3cebf6a586aa02cc4648bd2");
    }
}
