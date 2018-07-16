package io.rover.core.tracking

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import io.rover.core.logging.log
import io.rover.core.platform.whenNotNull

// emitter: handles observing the source, defining continuity for each event pair (UUID), perhaps
// remembering if necessary to handle process lifecycle.

// app emitter: it's own object, will use ProcessLifecycleObserver, will store a UUID across process
// restarts, expects Tracker to be injected.

// experience emitter:  each experience view model will expect the Tracker to be injected directly and it
// will call it. so, no actual emitter object.
// ExperienceViewModel will also have some sort of SessionEventProvider injected too, and provide that to

// the tracker: will simply emit, handle keep alive time (that is, will remember outstanding Starts
// and will end them on its own). could have one instance per concern, doesn't strictly matter.

// provider: will build the Event for start or finish. passed into the tracker at assembly time.

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
