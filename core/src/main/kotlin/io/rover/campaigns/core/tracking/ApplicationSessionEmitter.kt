package io.rover.campaigns.core.tracking

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.whenNotNull

/**
 * Monitors the application lifecycle and emits eventForSessionBoundary to the [SessionTrackerInterface]
 */
class ApplicationSessionEmitter(
    private val lifecycle: Lifecycle,
    private val tracker: SessionTrackerInterface
) {
    private var observer: LifecycleObserver? = null

    fun start() {
        lifecycle.addObserver(
            object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                fun appResume() {
                    tracker.enterSession(
                        "ApplicationSession",
                        "App Opened",
                        "App Viewed",
                        hashMapOf())
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                fun appPause() {
                    tracker.leaveSession(
                        "ApplicationSession",
                        "App Closed",
                        hashMapOf()
                    )
                }
            }
        )
        log.v("Application lifecycle tracking started.")
    }

    fun stop() {
        observer.whenNotNull { lifecycle.removeObserver(it) }
    }
}
