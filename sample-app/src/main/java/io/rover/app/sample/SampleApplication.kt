package io.rover.app.sample

import android.app.Application
import io.rover.sdk.Rover

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Rover.installSaneGlobalHttpCache(this)
        Rover.initialize(this, getString(R.string.rover_api_token))
    }
}
