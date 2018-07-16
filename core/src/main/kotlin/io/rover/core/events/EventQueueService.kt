package io.rover.core.events

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.rover.core.data.NetworkResult
import io.rover.core.data.domain.DeviceContext
import io.rover.core.data.domain.EventSnapshot
import io.rover.core.data.graphql.GraphQlApiServiceInterface
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.graphql.operations.data.asJson
import io.rover.core.data.graphql.operations.data.decodeJson
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.streams.subscribe
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.LocalStorage
import org.json.JSONArray
import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.Executors


class EventQueueService(
    private val graphQlApiService: GraphQlApiServiceInterface,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface,
    application: Application,
    private val flushAt: Int,
    private val flushIntervalSeconds: Double,
    private val maxBatchSize: Int,
    private val maxQueueSize: Int
) : EventQueueServiceInterface {
    private val serialQueueExecutor = Executors.newSingleThreadExecutor()
    private val contextProviders: MutableList<ContextProvider> = mutableListOf()
    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    // state:
    private val eventQueue: Deque<EventSnapshot> = LinkedList()
    private var deviceContext: DeviceContext? = null
    private var isFlushingEvents: Boolean = false

    override fun addContextProvider(contextProvider: ContextProvider) {
        serialQueueExecutor.execute {
            contextProviders.add(contextProvider)
        }
        contextProvider.registeredWithEventQueue(this)
    }

    override fun trackEvent(event: Event, namespace: String?) {
        log.v("Tracking event: $event")
        captureContext()
        enqueueEvent(event, namespace)
        flushEvents(flushAt)
    }

    override fun flushNow() {
        flushEvents(1)
    }

    private fun enqueueEvent(event: Event, namespace: String?) {
        serialQueueExecutor.execute {
            if(eventQueue.count() == maxQueueSize) {
                log.w("Event queue is at capacity ($maxQueueSize) -- removing oldest event.")
                eventQueue.removeFirst()
            }

            val snapshot = EventSnapshot.fromEvent(
                event,
                deviceContext ?: throw RuntimeException("enqueueEvent() occurred before Context set up?"),
                namespace
            )
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

        // I will borrow the JSON serialization extension methods from the Data Plugin.  Not exactly
        // optimal, but it will do for now.
        eventQueue.clear()

        val storedJson = keyValueStorage.get(Companion.QUEUE_KEY)

        if(storedJson != null) {
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

            if(eventQueue.isNotEmpty()) {
                log.v("Events queue with ${eventQueue.count()} events waiting has been restored.")
            }
        }
    }

    private fun flushEvents(minBatchSize: Int) {
        serialQueueExecutor.execute {
            if(isFlushingEvents) {
                log.v("Skipping flush, already in progress")
                return@execute
            }
            if(eventQueue.isEmpty()) {
                log.v("Skipping flush -- no events in the queue.")
                return@execute
            }
            if(eventQueue.count() < minBatchSize) {
                log.v("Skipping flush -- less than $minBatchSize events in the queue.")
                return@execute
            }

            val events = eventQueue.take(maxBatchSize)
            log.v("Uploading ${events.count()} event(s) to the Rover API.")

            isFlushingEvents = true

            graphQlApiService.submitEvents(events).subscribe { networkResult ->
                when(networkResult) {
                    is NetworkResult.Error -> {
                        log.i("Error delivering ${events.count()} events to the Rover API: ${networkResult.throwable.message}")

                        if(networkResult.shouldRetry) {
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
        if(singletonStartedGuard) {
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
                override fun onActivityPaused(activity: Activity?) {
                    log.d("An Activity is pausing, flushing Rover events queue.")
                    flushNow()
                }

                override fun onActivityResumed(activity: Activity?) { }

                override fun onActivityStarted(activity: Activity?) {  }

                override fun onActivityDestroyed(activity: Activity?) { }

                override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }

                override fun onActivityStopped(activity: Activity?) { }

                override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) { }
            }
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.rover.events-queue"
        private const val QUEUE_KEY = "queue"

        const val ROVER_NAMESPACE = "rover"

        private var singletonStartedGuard = false
    }
}
