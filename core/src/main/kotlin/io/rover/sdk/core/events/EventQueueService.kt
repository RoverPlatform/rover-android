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

package io.rover.sdk.core.events

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.rover.sdk.core.data.NetworkResult
import io.rover.sdk.core.data.domain.DeviceContext
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.graphql.operations.data.asJson
import io.rover.sdk.core.data.graphql.operations.data.decodeJson
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.share
import io.rover.sdk.core.streams.subscribe
import org.json.JSONArray
import org.reactivestreams.Publisher
import java.lang.AssertionError
import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.Executors

class EventQueueService(
    private val graphQlApiService: GraphQlApiServiceInterface,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface,
    application: Application,
    mainScheduler: Scheduler,
    private val flushAt: Int,
    private val flushIntervalSeconds: Double,
    private val maxBatchSize: Int,
    private val maxQueueSize: Int
) : EventQueueServiceInterface {
    private val serialQueueExecutor = Executors.newSingleThreadExecutor()
    private val contextProviders: MutableList<ContextProvider> = mutableListOf()
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private val eventSubject = PublishSubject<Event>()
    private val eventSnapshotSubject = PublishSubject<EventSnapshot>()

    // state:
    private val eventQueue: Deque<EventSnapshot> = LinkedList()
    private var deviceContext: DeviceContext? = null
    private var isFlushingEvents: Boolean = false

    override val trackedEvents: Publisher<Event> = eventSubject.observeOn(mainScheduler).share()

    override val enqueuedEvents: Publisher<EventSnapshot> = eventSnapshotSubject.observeOn(mainScheduler).share()

    override fun addContextProvider(contextProvider: ContextProvider) {
        serialQueueExecutor.execute {
            contextProviders.add(contextProvider)
        }
        contextProvider.registeredWithEventQueue(this)
    }

    override fun trackEvent(event: Event, namespace: String?) {
        try {
            log.v("Tracking event: $event")
        } catch (e: AssertionError) {
            // Workaround for a bug in Android that can cause crashes on Android 8.0 and 8.1 when
            // calling toString() on a java.util.Date
            log.w("Logging tracking event failed: $e")
        }

        captureContext()
        enqueueEvent(event, namespace)
        eventSubject.onNext(event)
        flushEvents(flushAt)
    }

    override fun trackScreenViewed(screenName: String, contentID: String?, contentName: String?) {
        val attributes = mutableMapOf<String, Any>("screenName" to screenName)

        contentName?.let { attributes.put("contentName", it) }
        contentID?.let { attributes.put("contentID", it) }

        trackEvent(Event("Screen Viewed", attributes))
    }

    override fun flushNow() {
        flushEvents(1)
    }

    private fun enqueueEvent(event: Event, namespace: String?) {
        serialQueueExecutor.execute {
            if (eventQueue.count() == maxQueueSize) {
                log.w("Event queue is at capacity ($maxQueueSize) -- removing oldest event.")
                eventQueue.removeFirst()
            }
            val snapshot = EventSnapshot.fromEvent(
                event,
                deviceContext ?: throw RuntimeException("enqueueEvent() occurred before Context set up?"),
                namespace,
            )

            // on main thread, send on eventSnapshotSubject
            Handler(Looper.getMainLooper()).post {
                eventSnapshotSubject.onNext(snapshot)
            }

            eventQueue.add(snapshot)
            persistEvents()
        }
    }

    private fun persistEvents() {
        serialQueueExecutor.execute {
            val json = JSONArray(eventQueue.map { event -> event.asJson(dateFormatting) }).toString(4)
            keyValueStorage.set(QUEUE_KEY, json)
        }
    }

    private fun restoreEvents() {
        // load the current events from key value storage.

        eventQueue.clear()

        val storedJson = keyValueStorage.get(QUEUE_KEY)

        if (storedJson != null) {
            val decoded = try {
                JSONArray(storedJson).getObjectIterable().map { jsonObject ->
                    EventSnapshot.decodeJson(jsonObject, dateFormatting)
                }
            } catch (e: Throwable) {
                log.w("Invalid persisted events queue.  Ignoring and starting fresh. ${e.message}")
                null
            }

            eventQueue.addAll(
                decoded ?: emptyList()
            )

            if (eventQueue.isNotEmpty()) {
                log.v("Events queue with ${eventQueue.count()} events waiting has been restored.")
            }
        }
    }

    private fun flushEvents(minBatchSize: Int) {
        serialQueueExecutor.execute {
            if (isFlushingEvents) {
                log.v("Skipping flush, already in progress")
                return@execute
            }
            if (eventQueue.isEmpty()) {
                log.v("Skipping flush -- no events in the queue.")
                return@execute
            }
            if (eventQueue.count() < minBatchSize) {
                log.v("Skipping flush -- less than $minBatchSize events in the queue.")
                return@execute
            }

            val events = eventQueue.take(maxBatchSize)
            log.v("Uploading ${events.count()} event(s) to the Rover API.")

            isFlushingEvents = true

            graphQlApiService.submitEvents(events).subscribe { networkResult ->
                when (networkResult) {
                    is NetworkResult.Error -> {
                        log.i("Error delivering ${events.count()} events to the Rover API: ${networkResult.throwable.message}")

                        if (networkResult.shouldRetry) {
                            log.i("... will leave them enqueued for a future retry.")
                        } else {
                            removeEvents(events)
                        }
                    }
                    is NetworkResult.Success -> {
                        log.v("Successfully uploaded ${events.count()} events.")
                        removeEvents(events)
                    }
                }
                isFlushingEvents = false
            }
        }
    }

    private fun removeEvents(eventsToRemove: List<EventSnapshot>) {
        val idsToRemove = eventsToRemove.associateBy { it.id }
        serialQueueExecutor.execute {
            eventQueue.clear()
            eventQueue.addAll(
                eventQueue.filter { existingEvent ->
                    !idsToRemove.containsKey(existingEvent.id)
                }
            )
            log.v("Removed ${eventsToRemove.count()} event(s) from the queue -- it now contains ${eventQueue.count()} event(s).")
            persistEvents()
        }
    }

    private fun captureContext() {
        serialQueueExecutor.execute {
            log.v("Capturing context...")
            deviceContext = contextProviders.fold(DeviceContext.blank()) { current, provider ->
                provider.captureContext(current)
            }
            log.v("Context is now: $deviceContext.")
        }
    }

    init {
        log.v("Starting up.")
        if (singletonStartedGuard) {
            throw RuntimeException("EventQueueService started twice.")
        }
        singletonStartedGuard = true

        restoreEvents()

        log.v("Starting $flushIntervalSeconds timer for submitting events.")

        // run a timer
        val handler = Handler(Looper.getMainLooper())
        fun scheduleFlushPoll() {
            handler.postDelayed({
                flushEvents(1)
                scheduleFlushPoll()
            }, flushIntervalSeconds.toLong() * 1000)
        }
        scheduleFlushPoll()

        // TODO: wire up Application-level activity callbacks after all to flush queue whenever an activity pauses.
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity: Activity) {
                    log.d("An Activity is pausing, flushing Rover events queue.")
                    flushNow()
                }

                override fun onActivityResumed(activity: Activity) { }

                override fun onActivityStarted(activity: Activity) { }

                override fun onActivityDestroyed(activity: Activity) { }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }

                override fun onActivityStopped(activity: Activity) { }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { }
            }
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "events-queue"
        private const val QUEUE_KEY = "queue"

        const val ROVER_NAMESPACE = "rover"

        private var singletonStartedGuard = false
    }
}
