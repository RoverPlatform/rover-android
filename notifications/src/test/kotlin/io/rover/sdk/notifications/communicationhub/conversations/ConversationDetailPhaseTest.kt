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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Covers what the conversation detail screen shows, which is a decision over the store and one
 * load state rather than over which coroutine finished last.
 */
class ConversationDetailPhaseTest {

    private val everyLoadState = ConversationLoadState.entries

    @Test
    fun `a conversation that is not in the store never renders the thread`() {
        everyLoadState.forEach { loadState ->
            assertNotEquals(
                "load state $loadState rendered the thread without a conversation",
                ConversationDetailPhase.Thread,
                ConversationDetailPhase.from(hasConversation = false, loadState = loadState),
            )
        }
    }

    @Test
    fun `a conversation in the store renders the thread whatever the load state says`() {
        everyLoadState.forEach { loadState ->
            assertEquals(
                "load state $loadState withheld the thread for a conversation that is present",
                ConversationDetailPhase.Thread,
                ConversationDetailPhase.from(hasConversation = true, loadState = loadState),
            )
        }
    }

    @Test
    fun `an absent conversation reported missing shows the not found state`() {
        assertEquals(
            ConversationDetailPhase.NotFound,
            ConversationDetailPhase.from(
                hasConversation = false,
                loadState = ConversationLoadState.NotFound,
            ),
        )
    }

    @Test
    fun `an absent conversation whose observation has died reports a load failure, not a missing one`() {
        // Nothing can fill the thread once the store's flow to the screen has failed, so waiting
        // is not an outcome: the screen has to say something. What it must not say is that the
        // conversation does not exist, because a conversation that does exist reaches here too —
        // the lookup can confirm it while the dead collector is unable to show it.
        assertEquals(
            ConversationDetailPhase.LoadFailed,
            ConversationDetailPhase.from(
                hasConversation = false,
                loadState = ConversationLoadState.Disconnected,
            ),
        )
    }

    @Test
    fun `an absent conversation that has not been reported missing is still loading`() {
        listOf(ConversationLoadState.Loading, ConversationLoadState.Showing).forEach { loadState ->
            assertEquals(
                "load state $loadState should still read as loading while nothing is in the store",
                ConversationDetailPhase.Loading,
                ConversationDetailPhase.from(hasConversation = false, loadState = loadState),
            )
        }
    }
}
