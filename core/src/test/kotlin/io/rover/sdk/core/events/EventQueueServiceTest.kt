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

import io.rover.sdk.core.data.GraphQlRequest
import io.rover.sdk.core.data.NetworkResult
import io.rover.sdk.core.data.domain.ClassicExperienceModel
import io.rover.sdk.core.data.domain.EventSnapshot
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.data.graphql.operations.FetchExperienceRequest
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.platform.DateFormatting
import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.Scheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers the queue bookkeeping around an upload that is in flight: events tracked while the
 * upload has not yet completed must survive the removal of the uploaded batch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EventQueueServiceTest {

    @Before
    fun clearSingletonGuard() {
        // EventQueueService refuses to start twice in a process, which would otherwise make the
        // second test in this class throw. Reach in and clear the guard between tests.
        EventQueueService::class.java.getDeclaredField("singletonStartedGuard").apply {
            isAccessible = true
            setBoolean(null, false)
        }
    }

    @Test
    fun `an event tracked while an upload is in flight survives the upload completing`() {
        val api = ControllableApiService()
        val storage = RecordingLocalStorage()
        val queue = eventQueueService(api, storage)

        queue.trackEvent(Event("Event A", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        val firstUpload = api.awaitUpload()
        assertEquals(listOf("Event A"), firstUpload.events.map { it.name })

        queue.trackEvent(Event("Event B", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        storage.awaitPersistedContaining("Event B")

        firstUpload.succeed()

        queue.flushNow()
        val secondUpload = api.awaitUpload()
        assertEquals(listOf("Event B"), secondUpload.events.map { it.name })
    }

    @Test
    fun `an event tracked while an upload is in flight survives a non-retryable failure`() {
        val api = ControllableApiService()
        val storage = RecordingLocalStorage()
        val queue = eventQueueService(api, storage)

        queue.trackEvent(Event("Event A", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        val firstUpload = api.awaitUpload()
        assertEquals(listOf("Event A"), firstUpload.events.map { it.name })

        queue.trackEvent(Event("Event B", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        storage.awaitPersistedContaining("Event B")

        firstUpload.fail(shouldRetry = false)

        queue.flushNow()
        val secondUpload = api.awaitUpload()
        assertEquals(listOf("Event B"), secondUpload.events.map { it.name })
    }

    @Test
    fun `an uploaded event is not uploaded again`() {
        val api = ControllableApiService()
        val storage = RecordingLocalStorage()
        val queue = eventQueueService(api, storage)

        queue.trackEvent(Event("Event A", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        api.awaitUpload().succeed()

        queue.flushNow()
        assertNull(api.uploads.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a retryable failure leaves the whole batch enqueued`() {
        val api = ControllableApiService()
        val storage = RecordingLocalStorage()
        val queue = eventQueueService(api, storage)

        queue.trackEvent(Event("Event A", hashMapOf()), EventQueueService.ROVER_NAMESPACE)
        api.awaitUpload().fail(shouldRetry = true)

        queue.flushNow()
        assertEquals(listOf("Event A"), api.awaitUpload().events.map { it.name })
    }

    private fun eventQueueService(
        api: GraphQlApiServiceInterface,
        storage: LocalStorage
    ): EventQueueService {
        return EventQueueService(
            graphQlApiService = api,
            localStorage = storage,
            dateFormatting = DateFormatting(),
            application = RuntimeEnvironment.getApplication(),
            mainScheduler = immediateScheduler,
            // flush on every tracked event, and never on the periodic timer, so that each test
            // drives the uploads itself.
            flushAt = 1,
            flushIntervalSeconds = 3600.0,
            maxBatchSize = 50,
            maxQueueSize = 1000
        )
    }

    private val immediateScheduler = object : Scheduler {
        override fun execute(runnable: () -> Unit) = runnable()
    }

    /**
     * A [GraphQlApiServiceInterface] whose event uploads never complete on their own: the test
     * decides when, and with what outcome, each one finishes.
     */
    private class ControllableApiService : GraphQlApiServiceInterface {
        val uploads = LinkedBlockingQueue<Upload>()

        class Upload(
            val events: List<EventSnapshot>,
            private val subscriber: Subscriber<in NetworkResult<String>>
        ) {
            fun succeed() {
                subscriber.onNext(NetworkResult.Success("accepted"))
            }

            fun fail(shouldRetry: Boolean) {
                subscriber.onNext(
                    NetworkResult.Error(RuntimeException("upload failed"), shouldRetry)
                )
            }
        }

        fun awaitUpload(): Upload {
            return uploads.poll(10, TimeUnit.SECONDS)
                ?: throw AssertionError("Timed out waiting for an event upload.")
        }

        override fun submitEvents(events: List<EventSnapshot>): Publisher<NetworkResult<String>> {
            return Publisher { subscriber ->
                subscriber.onSubscribe(object : Subscription {
                    override fun request(n: Long) { }
                    override fun cancel() { }
                })
                uploads.put(Upload(events, subscriber))
            }
        }

        override fun <TEntity> operation(
            request: GraphQlRequest<TEntity>
        ): Publisher<NetworkResult<TEntity>> = throw NotImplementedError()

        override fun fetchExperience(
            query: FetchExperienceRequest.ExperienceQueryIdentifier
        ): Publisher<NetworkResult<ClassicExperienceModel>> = throw NotImplementedError()
    }

    /**
     * In-memory [LocalStorage] that also records every value written, so that a test can wait for
     * the queue to have been persisted rather than sleeping.
     */
    private class RecordingLocalStorage : LocalStorage {
        private val values = ConcurrentHashMap<String, String>()
        private val writes = LinkedBlockingQueue<String>()

        fun awaitPersistedContaining(needle: String) {
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                val written = writes.poll(10, TimeUnit.SECONDS) ?: break
                if (written.contains(needle)) return
            }
            throw AssertionError("Timed out waiting for the queue containing $needle to persist.")
        }

        override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage {
            return object : KeyValueStorage {
                override fun get(key: String): String? = values[key]

                override fun set(key: String, value: String?) {
                    if (value == null) {
                        values.remove(key)
                    } else {
                        values[key] = value
                        writes.put(value)
                    }
                }

                override fun unset(key: String) {
                    values.remove(key)
                }

                override val keys: Set<String>
                    get() = values.keys.toSet()
            }
        }
    }
}
