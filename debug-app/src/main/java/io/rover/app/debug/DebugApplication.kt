package io.rover.app.debug

import android.app.Application
import io.rover.core.Rover
import timber.log.Timber

class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Rover.installSaneGlobalHttpCache(this)
        Rover.initialize(this, "BLANK")
    }
}
