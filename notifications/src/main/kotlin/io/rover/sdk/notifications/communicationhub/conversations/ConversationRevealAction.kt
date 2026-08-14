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

package io.rover.sdk.notifications.communicationhub.conversations

/**
 * The scroll behaviour owed to a replies-list transition, decided by [decideRevealAction].
 * Outgoing (fan) sends always force-scroll to the bottom,
 * incoming replies reveal only when the user is already near the bottom, and everything else
 * (history prepends, sync-state flips) leaves the scroll position alone.
 */
internal enum class RevealAction { ForceScroll, ScrollIfNearBottom, None }

/**
 * Decides the reveal action from a replies transition. Operates on replies rather than thread
 * rows because grouping and day dividers are presentation: a single send can insert zero, one,
 * or two list rows (a day-boundary send adds a divider plus a new group), so any row-count or
 * row-index heuristic is fragile.
 *
 * Backfill exclusion: only new replies that extend the tail — `sentAt >=` the previous newest
 * reply's `sentAt` — count as arrivals. Prepended history is older by construction, so this
 * holds even when an older page lands concurrently with a send.
 *
 * The optimistic→confirmed swap (local id replaced by the server id at the same `sentAt`,
 * `externalID` retained) reads as a new outgoing tail reply and yields
 * [RevealAction.ForceScroll] — same as iOS, where the confirmed reply's new server id
 * re-triggers the reveal. Harmless: by then the list is already pinned at the bottom.
 */
internal fun decideRevealAction(
    previous: List<ConversationReplyRow>,
    current: List<ConversationReplyRow>,
): RevealAction {
    // Initial population: the reversed list starts at index 0, already at the bottom.
    if (previous.isEmpty()) return RevealAction.None

    val previousIds = previous.mapTo(HashSet()) { it.id }
    val previousNewestSentAt = previous.maxOf { it.sentAt }
    val newTailReplies = current.filter { it.id !in previousIds && it.sentAt >= previousNewestSentAt }

    return when {
        newTailReplies.isEmpty() -> RevealAction.None
        newTailReplies.any { it.isOutgoing } -> RevealAction.ForceScroll
        else -> RevealAction.ScrollIfNearBottom
    }
}
