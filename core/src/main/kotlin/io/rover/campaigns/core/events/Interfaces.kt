package io.rover.campaigns.core.events

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.contextproviders.UserInfoContextProvider
import io.rover.campaigns.core.events.domain.Event
import org.reactivestreams.Publisher

/**
 * Objects that can contribute to a [DeviceContext] structure.
 *
 * Not to be confused with an Android context.
 */
interface ContextProvider {
    /**
     * Returns a new [DeviceContext] with the fields from the provided original ([deviceContext]) and the relevant
     * fields that this context provider can set.
     *
     * Typically will return immediately, but in some cases where a manual push token reset is
     * required it will block while doing some IO.  As such, do not use on the main thread.
     */
    fun captureContext(deviceContext: DeviceContext): DeviceContext

    /**
     * Called when this Context Provider is registered with the events queue.
     */
    fun registeredWithEventQueue(eventQueue: EventQueueServiceInterface) {}
}

/**
 * This should be informed of changes to the push token and ensures that it is transmitted up to the
 * Rover API.
 */
interface PushTokenTransmissionChannel {
    /**
     * Set the given token as the token that should be sent up to the API, at soonest convenience.
     *
     * Stateful; will remember the given token throughout the installation lifetime of the app until
     * it is reset.
     *
     * Asynchronous; will return immediately.
     */
    fun setPushToken(token: String?)
}

/**
 * The events queue is responsible for delivering [Event]s to the Rover cloud API.
 */
interface EventQueueServiceInterface {
    /**
     * Track the given [Event].  Enqueues it to be sent up to the Rover API.
     *
     * Asynchronous, will immediately return.
     *
     * @param namespace Specify a namespace name to your events in order to separate them from other
     * events. They will appear as a separate table in your BigQuery instance. May be left null to
     * have it appear in a default table.
     */
    fun trackEvent(event: Event, namespace: String? = null)

    /**
     * Track the given screen.  Enqueues it to be sent up to the Rover API.
     *
     * Asynchronous, will immediately return.
     */
    fun trackScreenViewed(
        screenName: String,
        contentID: String? = null,
        contentName: String? = null
    )

    /**
     * Install the given context provider, so that all outgoing events can the given context
     * provider populate some of the fields in a [DeviceContext].
     */
    fun addContextProvider(contextProvider: ContextProvider)

    /**
     * Enqueues an operation to flush any outstanding events to be executed immediately.
     *
     * Asynchronous, will immediately return.
     */
    fun flushNow()

    /**
     * Subscribe to this Publisher to be informed whenever a new Event is tracked into the Queue.
     */
    val trackedEvents: Publisher<Event>
}

interface UserInfoInterface {
    /**
     * Call this to set custom attributes to be included along with the [DeviceContext] given alongside
     * outgoing events.
     *
     * This will allow you to track any parameters you set from device-side in Rover Audience
     * or BigQuery, or use them for segmentation in Campaigns, or for personalization.
     *
     * Note that you may not use these attributes to address Campaigns to specific users with
     * personalization, because the threat model does not allow these values to be trusted.
     */
    fun update(builder: (attributes: HashMap<String, Any>) -> Unit)

    /**
     * Clear all the custom attributes.
     */
    fun clear()

    /**
     * Used by the [UserInfoContextProvider] to determine the current attributes to include
     * them in outgoing [Event]s.
     */
    val currentUserInfo: Attributes
}