package io.rover.core.events

import android.content.Intent
import android.support.v4.content.LocalBroadcastManager
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.share
import org.json.JSONObject
import org.reactivestreams.Publisher

/**
 * Provides a single source for Rover events being emitted.
 *
 * You can subscribe to the [trackedEvents] Publisher to be informed of them.
 *
 * Alternatively, this service emits them as local Android broadcast intents.  This allows consumers
 * to receive them that cannot otherwise link against types in this library.
 */
open class EventEmitter(
    private val localBroadcastManager: LocalBroadcastManager
) {
    protected open val eventSubject = PublishSubject<Event>()

    open val trackedEvents: Publisher<Event> by lazy {  eventSubject.share() }

    open fun trackEvent(event: Event) {
        log.w("EVENT TRACKED (${event.name}), but event emission with an internal broadcast intent not yet implemented.")


    }

    open fun trackEvent(action: String, attributes: Map<String, Any>) {
        val intent = Intent(
            action
        )
        // ANDREW START HERE AND SERIALIZE.
        intent.putExtra("attributes", JSONObject().pu)

        localBroadcastManager.sendBroadcast()
    }
}

