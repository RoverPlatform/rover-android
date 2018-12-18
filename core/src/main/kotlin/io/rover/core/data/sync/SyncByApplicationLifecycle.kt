package io.rover.core.data.sync

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.logging.log

/**
 * Run syncs (and event queue flushes) by process lifecycle.
 */
class SyncByApplicationLifecycle(
    private val syncCoordinator: SyncCoordinatorInterface,
    private val eventQueueService: EventQueueServiceInterface,
    private val processLifecycle: Lifecycle
) {
    private var observer: LifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun appResume() {
            log.v("App foregrounded, triggering sync.")
            syncCoordinator.triggerSync()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun appPause() {
            log.v("App backgrounded, triggering event flush.")
            eventQueueService.flushNow()
        }
    }

    fun start() {
        processLifecycle.addObserver(observer)
    }

    fun stop() {
        processLifecycle.removeObserver(observer)
    }
}
