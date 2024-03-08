/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.services

import android.app.Activity
import android.net.Uri
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Subscribe to this to receive events emitted by Experiences.
 *
 * Will also be responsible for dispatching Experience events to the Rover
 * event queue in the format needed by the cloud-side services.
 *
 * Note, this does not offer events for classic experiences. For that,
 * see [ClassicEventEmitter].
 */
internal class EventEmitter(
    private val eventQueueServiceInterface: EventQueueServiceInterface
) {
    private val sharedFlow: MutableSharedFlow<ExperienceEvent> = MutableSharedFlow()

    private val dispatcher = Dispatchers.IO

    internal val events = sharedFlow.asSharedFlow()

    internal fun emit(
        event: ExperienceEvent
    ) {
        CoroutineScope(dispatcher).launch {
            // emit the event to any listeners:
            sharedFlow.emit(event)

            // dispatch the event to the event queue (on main thread):
            launch(Dispatchers.Main) {
                event.toEventQueueFormat()?.let { roverEvent ->
                    eventQueueServiceInterface.trackEvent(
                        roverEvent,
                        "rover"
                    )
                }
            }
        }
    }
}

internal interface ExperienceEvent {
    /**
     * Create the [Event] type used for the event queue.
     *
     * Returns null if this event should not be sent to the event queue.
     */
    fun toEventQueueFormat(): Event?
}

internal data class ExperienceScreenViewed(
    val experienceName: String?,
    val experienceId: String?,
    val experienceUrl: Uri?,
    val screenName: String?,
    val screenId: String,
    val screenTags: List<String>,
    val screenProperties: Map<String, String>,
    val data: Any?,
    val urlParameters: Map<String, String>,
    val campaignId: String?,
) : ExperienceEvent {
    /**
     * Create the [Event] type used for the event queue.
     */
    override fun toEventQueueFormat(): Event {
        val experienceAttributes = mapOf(
            "name" to experienceName,
            "id" to experienceId,
            "campaignID" to campaignId,
            "url" to experienceUrl?.toString(),
        ).filterNullValues()

        val screenAttributes = mapOf(
            "name" to screenName,
            "id" to screenId,
        ).filterNullValues()

        val attributes: Map<String, Any> = mapOf(
            "experience" to experienceAttributes,
            "screen" to screenAttributes,
        ).filterNullValues()

        return Event(
            name = "Experience Screen Viewed",
            attributes = attributes,
        )
    }
}

internal data class CustomActionActivated(
    val experienceName: String?,
    val experienceId: String?,
    val experienceUrl: Uri?,
    val screenName: String?,
    val screenId: String,
    val screenTags: List<String>,
    val screenProperties: Map<String, String>,
    val data: Any?,
    val urlParameters: Map<String, String>,
    val campaignId: String?,

    val nodeName: String?,
    val nodeId: String,
    val nodeTags: List<String>,
    val nodeProperties: Map<String, String>,

    val activity: Activity?,
) : ExperienceEvent {
    override fun toEventQueueFormat(): Event? = null
}

internal data class ButtonTapped(
    val experienceName: String?,
    val experienceId: String?,
    val experienceUrl: Uri?,
    val screenName: String?,
    val screenId: String,
    val screenTags: List<String>,
    val screenProperties: Map<String, String>,
    val data: Any?,
    val urlParameters: Map<String, String>,
    val campaignId: String?,

    val nodeName: String?,
    val nodeId: String,
    val nodeTags: List<String>,
    val nodeProperties: Map<String, String>,
) : ExperienceEvent {
    override fun toEventQueueFormat(): Event {
        val experienceAttributes = mapOf(
            "name" to experienceName,
            "id" to experienceId,
            "campaignID" to campaignId,
            "url" to experienceUrl?.toString(),
        ).filterNullValues()

        val screenAttributes = mapOf(
            "name" to screenName,
            "id" to screenId,
        ).filterNullValues()

        val nodeAttributes = mapOf(
            "name" to nodeName,
            "id" to nodeId,
        ).filterNullValues()

        val attributes: Map<String, Any> = mapOf(
            "experience" to experienceAttributes,
            "screen" to screenAttributes,
            "node" to nodeAttributes,
        ).filterNullValues()

        return Event(
            name = "Experience Button Tapped",
            attributes = attributes,
        )
    }
}

private fun <K, V> Map<K, V?>.filterNullValues(): Map<K, V> {
    @Suppress("UNCHECKED_CAST")
    return this.filterValues { it != null } as Map<K, V>
}
