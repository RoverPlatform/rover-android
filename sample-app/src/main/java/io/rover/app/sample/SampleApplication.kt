package io.rover.app.sample

import android.app.Application
import io.rover.sdk.Rover

const val ROVER_API_TOKEN = "BLANK"

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Rover.installSaneGlobalHttpCache(this)
        Rover.initialize(this, ROVER_API_TOKEN)
    }
}
