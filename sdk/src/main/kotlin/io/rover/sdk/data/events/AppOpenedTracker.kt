package io.rover.sdk.data.events

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.rover.sdk.services.EventEmitter

internal class AppOpenedTracker(application: Application, private val eventEmitter: EventEmitter) {
    var runningActivities = 0

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { activityCreated() }
            override fun onActivityStarted(activity: Activity?) {}
            override fun onActivityResumed(activity: Activity?) {}
            override fun onActivityPaused(activity: Activity?) {}
            override fun onActivityStopped(activity: Activity?) {}
            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity?) { activityDestroyed() }
        })
    }

    fun activityCreated() {
        runningActivities =+ 1
        if (runningActivities == 1) trackAppOpenedEvent()
    }

    private fun trackAppOpenedEvent() {
        eventEmitter.trackEvent(RoverEvent.AppOpened())
    }

    fun activityDestroyed() {
        runningActivities =- 1
    }
}