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
import org.junit.Test

class StandaloneConversationVisibilityTrackerTest {
    @Test
    fun visibleConversationIsPublished() {
        val tracker = StandaloneConversationVisibilityTracker()

        tracker.updateConversationVisibility("conversation-1", isVisible = true)

        assertThat(
            tracker.visibilityState.value,
            equalTo(StandaloneConversationVisibilityState.ShowingConversation("conversation-1")),
        )
    }

    @Test
    fun hiddenConversationClearsMatchingVisibleConversation() {
        val tracker = StandaloneConversationVisibilityTracker()

        tracker.updateConversationVisibility("conversation-1", isVisible = true)
        tracker.updateConversationVisibility("conversation-1", isVisible = false)

        assertThat(tracker.visibilityState.value, equalTo(StandaloneConversationVisibilityState.Hidden))
    }

    @Test
    fun staleHiddenCallbackDoesNotClearNewerConversation() {
        val tracker = StandaloneConversationVisibilityTracker()

        tracker.updateConversationVisibility("conversation-1", isVisible = true)
        tracker.updateConversationVisibility("conversation-2", isVisible = true)
        tracker.updateConversationVisibility("conversation-1", isVisible = false)

        assertThat(
            tracker.visibilityState.value,
            equalTo(StandaloneConversationVisibilityState.ShowingConversation("conversation-2")),
        )
    }
}
