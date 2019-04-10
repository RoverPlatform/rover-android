package io.rover.app.debug

import android.app.Application
import com.appspector.sdk.AppSpector
import io.rover.core.Rover
import timber.log.Timber

class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // You can start all monitors
        AppSpector
            .build(this)
            .withDefaultMonitors()
            .run("android_NWJhNTMwYTMtM2RlOS00MzFlLWEzMWMtMTU5YWUzNjBlZjdk");

        Rover.installSaneGlobalHttpCache(this)
        Rover.initialize(this, "6c546189dc45df1293bddc18c0b54786")
    }
}
