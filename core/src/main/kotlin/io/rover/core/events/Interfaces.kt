package io.rover.core.events

import io.rover.core.events.domain.Event
import org.reactivestreams.Publisher

/**
 * Emit an event describing something that has expired in the Rover SDK such that other services can subscribe to it.
 *
 * TODO: rename to EventEmitter.
 */
interface EventEmitterInterface {
    /**
     * Track the given [Event].  Enqueues it to be sent up to the Rover API.
     *
     * Asynchronous, will immediately return.
     */
    fun trackEvent(event: Event)


    /**
     * Subscribe to this Publisher to be informed whenever a new Event is tracked into the Queue.
     */
    val trackedEvents: Publisher<Event>
}
