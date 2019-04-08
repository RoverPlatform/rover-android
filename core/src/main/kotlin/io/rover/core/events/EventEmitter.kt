package io.rover.core.events

import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.share
import org.reactivestreams.Publisher

open class EventEmitter(

) : EventEmitterInterface {

    // maybe use?
    protected val eventSubject = PublishSubject<Event>()

    override val trackedEvents: Publisher<Event> = eventSubject.share()

    override fun trackEvent(event: Event) {
        log.w("EVENT TRACKED (${event.name}), but event emission with an internal broadcast intent not yet implemented.")
    }
}
