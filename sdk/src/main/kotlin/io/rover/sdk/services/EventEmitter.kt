package io.rover.sdk.services

import io.rover.sdk.data.events.RoverEvent
import io.rover.sdk.streams.PublishSubject
import io.rover.sdk.streams.share
import org.reactivestreams.Publisher

/**
 * Provides a single source for Rover events being emitted.
 *
 * You can subscribe to the [trackedEvents] Publisher to be informed of them.
 *
 * Alternatively, this service emits them as local Android broadcast intents.  This allows consumers
 * to receive them that cannot otherwise link against types in this library.
 */
open class EventEmitter {
    protected open val eventSubject = PublishSubject<RoverEvent>()

    open val trackedEvents: Publisher<RoverEvent> by lazy {  eventSubject.share() }

    open fun trackEvent(roverEvent: RoverEvent) {
            eventSubject.onNext(roverEvent)
            when(roverEvent) {
                is RoverEvent.BlockTapped -> listeners.forEach { it.onBlockTapped(roverEvent) }
                is RoverEvent.ExperienceDismissed -> listeners.forEach { it.onExperienceDismissed(roverEvent) }
                is RoverEvent.ScreenDismissed -> listeners.forEach { it.onScreenDismissed(roverEvent) }
                is RoverEvent.ExperiencePresented -> listeners.forEach { it.onExperiencePresented(roverEvent) }
                is RoverEvent.ExperienceViewed -> listeners.forEach { it.onExperienceViewed(roverEvent) }
                is RoverEvent.ScreenViewed -> listeners.forEach { it.onScreenViewed(roverEvent) }
                is RoverEvent.ScreenPresented -> listeners.forEach { it.onScreenPresented(roverEvent) }
            }
    }

    private val listeners: MutableList<RoverEventListener> = mutableListOf()

    fun addEventListener(listener: RoverEventListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: RoverEventListener) {
        listeners.remove(listener)
    }

    fun removeAllListeners() {
        listeners.clear()
    }
}

interface RoverEventListener {
    fun onBlockTapped(event: RoverEvent.BlockTapped) {}

    fun onExperienceDismissed(event: RoverEvent.ExperienceDismissed) {}

    fun onScreenDismissed(event: RoverEvent.ScreenDismissed) {}

    fun onExperiencePresented(event: RoverEvent.ExperiencePresented) {}

    fun onExperienceViewed(event: RoverEvent.ExperienceViewed) {}

    fun onScreenViewed(event: RoverEvent.ScreenViewed) {}

    fun onScreenPresented(event: RoverEvent.ScreenPresented) {}
}