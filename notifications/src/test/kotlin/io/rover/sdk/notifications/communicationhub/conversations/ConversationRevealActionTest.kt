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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T0
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T1
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T2
import org.junit.Test

class ConversationRevealActionTest {

    @Test
    fun newOutgoingTailReplyForcesScroll() {
        // The optimistic insert on send: queued, sentAt is local "now" (the max).
        val previous = listOf(
            incoming("in-1", sentAt = T0),
            incoming("in-2", sentAt = T1),
        )
        val current = previous + outgoing("local-1", sentAt = T2, syncState = ReplyEntity.SYNC_STATE_QUEUED)

        assertThat(decideRevealAction(previous, current), equalTo(RevealAction.ForceScroll))
    }

    @Test
    fun optimisticToConfirmedSwapForcesScrollAndIsNeverMisreadAsPrepend() {
        // On confirmation the local id is replaced by the server id (externalID retained,
        // same sentAt). The new server id reads as a new outgoing tail reply → ForceScroll,
        // matching iOS where the confirmation re-triggers the reveal. Harmless: the list is
        // already pinned at the bottom by the optimistic insert. Pinned here so it can never
        // regress into being treated as a prepend (None would also be tolerable, but a
        // positional gate must not re-appear on this path).
        val previous = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("local-1", sentAt = T1, externalID = "ext-1", syncState = ReplyEntity.SYNC_STATE_QUEUED),
        )
        val current = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("server-1", sentAt = T1, externalID = "ext-1", syncState = ReplyEntity.SYNC_STATE_CONFIRMED),
        )

        assertThat(decideRevealAction(previous, current), equalTo(RevealAction.ForceScroll))
    }

    @Test
    fun newIncomingTailReplyScrollsIfNearBottom() {
        val previous = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("out-1", sentAt = T1),
        )
        val current = previous + incoming("in-2", sentAt = T2)

        assertThat(decideRevealAction(previous, current), equalTo(RevealAction.ScrollIfNearBottom))
    }

    @Test
    fun prependedOlderPageDoesNotScroll() {
        // Backward pagination: the older page is prepended, and may well contain the fan's
        // own (outgoing) historical replies — those must not read as a fresh send.
        val previous = listOf(
            incoming("in-2", sentAt = T1),
            outgoing("out-2", sentAt = T2),
        )
        val current = listOf(
            outgoing("out-1", sentAt = T0),
            incoming("in-1", sentAt = T0),
        ) + previous

        assertThat(decideRevealAction(previous, current), equalTo(RevealAction.None))
    }

    @Test
    fun syncStateOnlyChangeDoesNotScroll() {
        val previous = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("out-1", sentAt = T1, syncState = ReplyEntity.SYNC_STATE_QUEUED),
        )
        val current = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("out-1", sentAt = T1, syncState = ReplyEntity.SYNC_STATE_FAILED),
        )

        assertThat(decideRevealAction(previous, current), equalTo(RevealAction.None))
    }

    @Test
    fun initialPopulationFromEmptyDoesNotScroll() {
        // First page load into an empty list: the reversed list is already positioned at
        // index 0, the bottom.
        val current = listOf(
            incoming("in-1", sentAt = T0),
            outgoing("out-1", sentAt = T1),
        )

        assertThat(decideRevealAction(emptyList(), current), equalTo(RevealAction.None))
    }

    private fun incoming(id: String, sentAt: Long) = ConversationTestFixtures.replyRow(
        id = id,
        sentAt = sentAt,
        isOutgoing = false,
    )

    private fun outgoing(
        id: String,
        sentAt: Long,
        externalID: String? = null,
        syncState: String = ReplyEntity.SYNC_STATE_CONFIRMED,
    ) = ConversationTestFixtures.replyRow(
        id = id,
        senderId = null,
        senderName = null,
        externalID = externalID,
        isOutgoing = true,
        sentAt = sentAt,
        syncState = syncState,
    )
}
