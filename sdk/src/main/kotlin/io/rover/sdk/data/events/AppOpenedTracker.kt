package io.rover.sdk.data.events

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.rover.sdk.services.EventEmitter

internal class AppOpenedTracker(private val eventEmitter: EventEmitter) {
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onStart() { trackAppOpenedEvent() }
        })
    }

    private fun trackAppOpenedEvent() {
        eventEmitter.trackEvent(RoverEvent.AppOpened())
    }
}