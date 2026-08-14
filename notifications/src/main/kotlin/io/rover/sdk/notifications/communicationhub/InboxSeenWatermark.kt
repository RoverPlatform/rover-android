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

import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted record of how far through the inbox the user has seen: the newest item activity
 * timestamp the user has looked at — post `receivedAt` or conversation activity — or, on a fresh
 * install or identity reset, the device instant at which the watermark was seeded.
 *
 * The Hub badge counts items that are *both* unread *and* have activity after this watermark. Read
 * state alone is not enough: it is per-item and long-lived, so a user who scans the inbox without
 * tapping individual items would carry their entire history in the badge forever. The watermark is
 * the "the user has looked at the inbox" signal that read state cannot express.
 *
 * Requiring *both* conditions is what makes the badge clearable from either direction: reading an
 * item anywhere removes it from the count — including on the deep-link/push-tap detail screen,
 * which never composes the messages list and so never moves the watermark — while visiting the
 * inbox advances the watermark and clears everything delivered up to that moment at once.
 *
 * [markSeen] only ever stores a timestamp the caller has actually observed on a stored item, never
 * the device clock: the watermark is compared against those same stored timestamps, so writing the
 * value the comparison uses keeps the two sides consistent by construction, and a monotonic
 * observed-value watermark can never mark an item the user has not had in front of them as seen.
 *
 * **Fresh install / first upgrade:** when no value is stored yet the watermark is initialized to
 * (and persisted at) the moment of first load, so already-synced history never badges. This is the
 * core of SDK-347: without it, apps upgrading into the watermark scheme would surface every
 * historical post at once.
 *
 * Seeding ([loadOrInitialize]) and [resetToNow] are the writes that use the device clock, because
 * at those two points there are no stored items to anchor to.
 *
 * The stored representation is an ISO-8601 instant string (matching
 * `LastSeenContextProvider`'s convention); the exposed value is epoch milliseconds to line up with
 * the entity timestamps.
 *
 * Modelled on `HomeViewManager`: the persisted value is loaded during construction so the watermark
 * is available synchronously to the badge on the first emission of the posts flow.
 */
internal class InboxSeenWatermark(
    localStorage: LocalStorage,
    private val now: () -> Instant = Instant::now,
) {
    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private val _seenAt = MutableStateFlow(loadOrInitialize())

    /**
     * Epoch milliseconds of the last time the inbox was seen.
     *
     * Never null: on a fresh install this is the time the SDK first loaded the watermark.
     */
    val seenAt: StateFlow<Long> = _seenAt.asStateFlow()

    /**
     * Records that the user has seen the inbox up to [newestActivityAtEpochMillis] — the newest
     * activity timestamp among the posts and conversations currently in the store — persisting and
     * publishing the new watermark.
     *
     * Called when the messages list is revealed, again when it stops being visible, and whenever a
     * feed emission brings in something newer while the list is on screen, so items the user has
     * actually had in front of them do not badge afterwards.
     *
     * **The argument is the new watermark, not a floor.** The device clock is deliberately not
     * consulted here; the watermark is written from the same stored timestamps it is compared
     * against (see the class documentation). When the inbox has been seen, every item currently
     * persisted has by definition been seen, so storing the newest stored timestamp can never mark
     * an unseen item as seen.
     *
     * Null (an entirely empty inbox, e.g. a reveal racing the first query) is a no-op: there is
     * nothing for the watermark to absorb. A value at or before the current watermark is likewise
     * a no-op — the watermark only ever moves forwards, so a stale caller cannot re-badge items
     * the user has already seen.
     *
     * The whole compare/persist/publish sequence is synchronized because this is reachable both
     * from main-thread lifecycle callbacks and from the feed flow's collector; without it, two
     * concurrent writers could interleave such that an older timestamp overwrites a newer watermark.
     */
    @Synchronized
    fun markSeen(newestActivityAtEpochMillis: Long?) {
        val epochMillis = newestActivityAtEpochMillis ?: return
        if (epochMillis <= _seenAt.value) return

        // Build the Instant back from the epoch millis so that the persisted ISO-8601 string and
        // the published value can never disagree.
        val timestamp = Instant.ofEpochMilli(epochMillis)
        persist(timestamp)
        _seenAt.value = epochMillis
        log.i("Inbox seen watermark advanced to $timestamp")
    }

    /**
     * Resets the watermark to the current device time, used on a 410 reset.
     *
     * Named separately because at the reset site the intent is to discard a watermark belonging to
     * the previous identity, not to record that the user saw anything. Unlike [markSeen], this is
     * intentionally not monotonic and may replace a later watermark with the current time.
     *
     * This is one of the two writes that use the device clock (the other being the first-load
     * seed): the item stores are being wiped as part of the reset, so there is no stored timestamp
     * left to anchor to.
     */
    @Synchronized
    fun resetToNow() {
        val timestamp = now()
        persist(timestamp)
        _seenAt.value = timestamp.toEpochMilli()
        log.i("Inbox seen watermark reset to $timestamp")
    }

    private fun loadOrInitialize(): Long {
        val stored = storage[WATERMARK_KEY]
        if (stored != null) {
            try {
                return Instant.parse(stored).toEpochMilli()
            } catch (error: Exception) {
                log.w("Unable to parse stored inbox seen watermark '$stored', reinitializing: ${error.message}")
            }
        }

        // First load (fresh install, or first run after upgrading into the watermark scheme):
        // treat everything already delivered as seen. Along with resetToNow(), this is one of the
        // two device-clock writes — there is no observed item timestamp to anchor to before the
        // first feed emission.
        val initial = now()
        persist(initial)
        log.i("Initialized inbox seen watermark to $initial")
        return initial.toEpochMilli()
    }

    private fun persist(timestamp: Instant) {
        storage[WATERMARK_KEY] = timestamp.toString()
    }

    companion object {
        internal const val STORAGE_CONTEXT_IDENTIFIER = "hub-inbox-seen-watermark"
        internal const val WATERMARK_KEY = "io.rover.hub.inboxSeenWatermark"
    }
}
