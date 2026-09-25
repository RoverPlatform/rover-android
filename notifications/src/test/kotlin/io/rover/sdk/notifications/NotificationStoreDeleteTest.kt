/*
 * Copyright (c) 2026, Rover Labs, Inc. All rights reserved.
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

package io.rover.sdk.notifications

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.platform.DateFormatting
import io.rover.sdk.core.platform.KeyValueStorage
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.forExecutor
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.graphql.decodeJson
import io.rover.sdk.notifications.graphql.encodeJson
import java.util.Date
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduction for SDK-362: notifications are not (reliably) deleted when [NotificationStore.delete]
 * is invoked multiple times in quick succession, as integrators feeding their own inbox UIs do
 * when the user clears several messages at once.
 *
 * Each delete action performs a read-modify-write cycle against the SharedPreferences-backed
 * cache on the multi-threaded I/O executor, with nothing serializing the cycles, so concurrent
 * deletes can each read the same starting list and the last write wins — losing earlier deletes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationStoreDeleteTest {

    private val dateFormatting = DateFormatting()

    /**
     * Thread-safe in-memory stand-in for the SharedPreferences-backed storage: individual reads
     * and writes are atomic (as SharedPreferences' are), but read-modify-write cycles are not.
     */
    private class SynchronizedLocalStorage : LocalStorage {
        private val stores = mutableMapOf<String, KeyValueStorage>()

        @Synchronized
        override fun getKeyValueStorageFor(namedContext: String): KeyValueStorage =
            stores.getOrPut(namedContext) {
                object : KeyValueStorage {
                    private val values = mutableMapOf<String, String>()

                    @Synchronized
                    override fun get(key: String): String? = values[key]

                    @Synchronized
                    override fun set(key: String, value: String?) {
                        if (value == null) values.remove(key) else values[key] = value
                    }

                    @Synchronized
                    override fun unset(key: String) {
                        values.remove(key)
                    }

                    override val keys: Set<String>
                        @Synchronized
                        get() = values.keys.toSet()
                }
            }
    }

    private fun testNotification(index: Int): Notification = Notification(
        id = "notification-$index",
        channelId = null,
        title = "test",
        body = "test notification $index",
        isRead = false,
        isDeleted = false,
        isNotificationCenterEnabled = false,
        expiresAt = null,
        deliveredAt = Date(1755000000000L - index * 1000L),
        tapBehavior = Notification.TapBehavior.OpenApp(),
        attachment = null,
        campaignId = "campaign-$index",
        conversionTags = null
    )

    @Test
    fun deletingSeveralNotificationsInQuickSuccessionDeletesAllOfThem() {
        val localStorage = SynchronizedLocalStorage()
        val keyValueStorage = localStorage.getKeyValueStorageFor("notification-storage")

        val notifications = (0 until 30).map { testNotification(it) }

        // Seed the on-disk cache directly, as a prior sync would have.
        keyValueStorage["local-notifications-cache"] =
            JSONArray(notifications.map { it.encodeJson(dateFormatting) }).toString()

        // mirrors production: a wide multi-threaded I/O pool.
        val ioExecutor = Executors.newCachedThreadPool()
        val eventQueue = mock<EventQueueServiceInterface>()
        try {
            val store = NotificationStore(
                dateFormatting = dateFormatting,
                ioExecutor = ioExecutor,
                mainThreadScheduler = Scheduler.forExecutor(Executor { it.run() }),
                eventQueue = eventQueue,
                syncCoordinator = mock<SyncCoordinatorInterface> {
                    on { syncResults } doReturn PublishSubject()
                },
                localStorage = localStorage
            )

            // Integrator deletes every notification, one delete() call per notification, back to back.
            notifications.forEach { store.delete(it) }

            fun onDisk(): List<Notification> =
                JSONArray(keyValueStorage["local-notifications-cache"]!!).getObjectIterable().map {
                    Notification.decodeJson(it, dateFormatting)
                }

            // Wait for the store to go quiescent: stop once the persisted state has been stable
            // (and fully deleted) for a while, or give up after the deadline.
            val deadline = System.currentTimeMillis() + 10_000
            var stableSince: Long? = null
            var previous: String? = null
            while (System.currentTimeMillis() < deadline) {
                val current = keyValueStorage["local-notifications-cache"]
                if (current == previous) {
                    if (onDisk().all { it.isDeleted }) break
                    if (stableSince == null) stableSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - stableSince > 2_000) break
                } else {
                    previous = current
                    stableSince = null
                }
                Thread.sleep(50)
            }

            val survivors = onDisk().filter { !it.isDeleted }.map { it.id }
            assertThat("notifications still not deleted: $survivors", survivors.isEmpty(), equalTo(true))

            // Each delete is a real state transition, so exactly one "Notification Marked Deleted"
            // event should have been tracked per notification.  Event tracking happens after the
            // persisted write polled above, so allow the outstanding callbacks time to land.
            verify(eventQueue, timeout(5_000).times(notifications.size)).trackEvent(any(), any())
        } finally {
            ioExecutor.shutdown()
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
