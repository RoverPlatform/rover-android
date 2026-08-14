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

package io.rover.sdk.notifications.communicationhub

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.greaterThanOrEqualTo
import io.rover.sdk.core.platform.LocalStorage
import java.time.Instant
import org.junit.Test

class InboxSeenWatermarkTest {

    @Test
    fun freshInstallInitializesWatermarkToNow() {
        val now = Instant.parse("2026-08-10T12:00:00Z")

        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { now })

        assertThat(watermark.seenAt.value, equalTo(now.toEpochMilli()))
    }

    @Test
    fun freshInstallPersistsTheInitializedWatermark() {
        val storage = InMemoryLocalStorage()
        val now = Instant.parse("2026-08-10T12:00:00Z")

        InboxSeenWatermark(storage, now = { now })

        assertThat(
            storedWatermark(storage),
            equalTo(now.toString()),
        )
    }

    @Test
    fun freshInstallUsesRealClockByDefault() {
        val before = System.currentTimeMillis()

        val watermark = InboxSeenWatermark(InMemoryLocalStorage())

        assertThat(watermark.seenAt.value, greaterThanOrEqualTo(before))
    }

    @Test
    fun watermarkIsLoadedFromStorageOnSubsequentInstances() {
        val storage = InMemoryLocalStorage()
        val firstRun = Instant.parse("2026-08-10T12:00:00Z")
        InboxSeenWatermark(storage, now = { firstRun })

        // A later process start must not re-initialize to "now"; the stored value wins.
        val secondRun = Instant.parse("2026-08-11T12:00:00Z")
        val reloaded = InboxSeenWatermark(storage, now = { secondRun })

        assertThat(reloaded.seenAt.value, equalTo(firstRun.toEpochMilli()))
    }

    @Test
    fun markSeenPublishesAndPersistsTheNewWatermark() {
        val storage = InMemoryLocalStorage()
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(storage, now = { clock })

        val newestReceivedAt = Instant.parse("2026-08-10T13:30:00Z")
        watermark.markSeen(newestReceivedAt.toEpochMilli())

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(newestReceivedAt.toString()))
        assertThat(
            InboxSeenWatermark(storage, now = { Instant.parse("2026-08-12T00:00:00Z") }).seenAt.value,
            equalTo(newestReceivedAt.toEpochMilli()),
        )
    }

    @Test
    fun markSeenAdvancesToTheGivenTimestampWhenTheDeviceClockIsBehind() {
        val storage = InMemoryLocalStorage()
        var clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(storage, now = { clock })

        // The device clock lags the newest stored timestamp: a post stamped 13:30 would
        // otherwise keep badging after the user has looked at it.
        clock = Instant.parse("2026-08-10T12:05:00Z")
        val newestReceivedAt = Instant.parse("2026-08-10T13:30:00Z")
        watermark.markSeen(newestReceivedAt.toEpochMilli())

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(newestReceivedAt.toString()))
        assertThat(
            InboxSeenWatermark(storage, now = { Instant.parse("2026-08-12T00:00:00Z") }).seenAt.value,
            equalTo(newestReceivedAt.toEpochMilli()),
        )
    }

    @Test
    fun markSeenIgnoresATimestampOlderThanTheWatermarkEvenWhenTheDeviceClockIsAhead() {
        val storage = InMemoryLocalStorage()
        val seed = Instant.parse("2026-08-10T14:00:00Z")
        var clock = seed
        val watermark = InboxSeenWatermark(storage, now = { clock })

        // The device clock races ahead of the seed, but markSeen must not consult it: the only
        // thing it is told about is a post older than the watermark, which changes nothing.
        clock = Instant.parse("2026-08-10T15:00:00Z")
        watermark.markSeen(Instant.parse("2026-08-10T13:30:00Z").toEpochMilli())

        assertThat(watermark.seenAt.value, equalTo(seed.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(seed.toString()))
    }

    @Test
    fun markSeenDoesNotMoveTheWatermarkBackwards() {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        // A stored-item timestamp ahead of the device clock.
        val advancedWatermark = Instant.parse("2026-08-10T13:30:00Z")
        watermark.markSeen(advancedWatermark.toEpochMilli())

        // A subsequent mark carrying an older newest-post timestamp (a post store that has shrunk,
        // or a stale caller) must not undo the watermark and re-badge posts already seen.
        watermark.markSeen(Instant.parse("2026-08-10T13:00:00Z").toEpochMilli())

        assertThat(watermark.seenAt.value, equalTo(advancedWatermark.toEpochMilli()))
    }

    @Test
    fun markSeenWithNullIsANoOp() {
        val storage = InMemoryLocalStorage()
        val seed = Instant.parse("2026-08-10T12:00:00Z")
        var clock = seed
        val watermark = InboxSeenWatermark(storage, now = { clock })

        // An empty inbox has nothing for the watermark to absorb, and the device clock must not
        // stand in for the missing item timestamp — so neither the published nor the persisted
        // value moves.
        clock = Instant.parse("2026-08-10T13:30:00Z")
        watermark.markSeen(null)

        assertThat(watermark.seenAt.value, equalTo(seed.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(seed.toString()))
    }

    /**
     * Regression test pinning that markSeen never lets the device clock leak into the watermark:
     * an earlier revision wrote "now" on every inbox visit, which planted the watermark ahead of
     * every stored item, and items stamped between the newest seen item and that instant silently
     * never badged (permanently, since the watermark only moves forwards).
     */
    @Test
    fun markSeenIgnoresADeviceClockAheadOfTheStoredItems() {
        val storage = InMemoryLocalStorage()
        var deviceClock = Instant.parse("2026-08-10T11:00:00Z")
        val watermark = InboxSeenWatermark(storage, now = { deviceClock })

        // The device clock has run on to 13:00 when the user visits the inbox; the newest stored
        // item is stamped 12:00.
        deviceClock = Instant.parse("2026-08-10T13:00:00Z")
        val newestSeenPost = Instant.parse("2026-08-10T12:00:00Z")
        watermark.markSeen(newestSeenPost.toEpochMilli())

        assertThat(watermark.seenAt.value, equalTo(newestSeenPost.toEpochMilli()))

        // An item stamped a minute after the newest seen one lands between the watermark and the
        // device clock: a device-clock watermark (13:00) would have suppressed it permanently. It
        // must count as new.
        val arrivedAfterwards = Instant.parse("2026-08-10T12:01:00Z")
        assertThat(arrivedAfterwards.toEpochMilli() > watermark.seenAt.value, equalTo(true))
    }

    @Test
    fun resetToNowUsesTheDeviceClock() {
        val storage = InMemoryLocalStorage()
        var clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(storage, now = { clock })

        // The item stores are wiped by the 410 reset, so there is no stored timestamp left to
        // anchor to; the device clock is the only reference available.
        clock = Instant.parse("2026-08-10T12:05:00Z")
        watermark.resetToNow()

        assertThat(watermark.seenAt.value, equalTo(clock.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(clock.toString()))
    }

    @Test
    fun resetToNowCanMoveAWatermarkBackToTheDeviceClock() {
        val storage = InMemoryLocalStorage()
        var clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(storage, now = { clock })
        val aheadOfDeviceClock = Instant.parse("2026-08-10T13:30:00Z")
        watermark.markSeen(aheadOfDeviceClock.toEpochMilli())

        clock = Instant.parse("2026-08-10T12:05:00Z")
        watermark.resetToNow()

        assertThat(watermark.seenAt.value, equalTo(clock.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(clock.toString()))
    }

    @Test
    fun resetToNowAdvancesTheWatermark() {
        var clock = Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })

        clock = Instant.parse("2026-08-10T14:00:00Z")
        watermark.resetToNow()

        assertThat(watermark.seenAt.value, equalTo(clock.toEpochMilli()))
    }

    @Test
    fun unparseableStoredWatermarkFallsBackToNow() {
        val storage = InMemoryLocalStorage()
        storage.getKeyValueStorageFor(STORAGE_CONTEXT)[InboxSeenWatermark.WATERMARK_KEY] = "not-a-timestamp"
        val now = Instant.parse("2026-08-10T12:00:00Z")

        val watermark = InboxSeenWatermark(storage, now = { now })

        assertThat(watermark.seenAt.value, equalTo(now.toEpochMilli()))
        assertThat(storedWatermark(storage), equalTo(now.toString()))
    }

    private fun storedWatermark(storage: LocalStorage): String? =
        storage.getKeyValueStorageFor(STORAGE_CONTEXT)[InboxSeenWatermark.WATERMARK_KEY]

    private companion object {
        const val STORAGE_CONTEXT = InboxSeenWatermark.STORAGE_CONTEXT_IDENTIFIER
    }
}
