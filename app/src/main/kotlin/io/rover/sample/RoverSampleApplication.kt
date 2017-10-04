package io.rover.sample

import android.app.Application
import com.facebook.stetho.Stetho

/**
 * Sample app entry point.
 */
class RoverSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)
    }
}
