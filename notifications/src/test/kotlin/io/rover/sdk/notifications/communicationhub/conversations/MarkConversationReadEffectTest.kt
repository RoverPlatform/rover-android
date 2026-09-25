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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A reply is requested as read only while the user can see it: lifecycle at least STARTED and
 * near the bottom of the thread, re-checked when either changes or a newer reply lands. The
 * effect itself never deduplicates; that is the view model's job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkConversationReadEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestLifecycleOwner(initialState: Lifecycle.State = Lifecycle.State.RESUMED) : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = initialState }
        override val lifecycle: Lifecycle get() = registry
    }

    private class Harness {
        var owner by mutableStateOf(TestLifecycleOwner())
        val marked = mutableListOf<String>()
        var latestReplyId by mutableStateOf<String?>(null)
        var nearBottom by mutableStateOf(true)
    }

    private fun compose(harness: Harness) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides harness.owner) {
                MarkConversationReadEffect(
                    latestReplyId = harness.latestReplyId,
                    nearBottom = harness.nearBottom,
                    markRead = { replyId -> harness.marked += replyId },
                )
            }
        }
    }

    @Test
    fun aReplyRenderedAtTheBottomOfAStartedScreenIsMarkedRead() {
        val harness = Harness()
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()

        assertThat(harness.marked, equalTo(listOf("reply-1")))
    }

    @Test
    fun aReplyArrivingWhileStoppedIsMarkedOnlyOnceTheScreenStarts() {
        val harness = Harness()
        compose(harness)
        composeRule.waitForIdle()
        composeRule.runOnIdle { harness.owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }

        harness.latestReplyId = "reply-1"
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(emptyList()))

        composeRule.runOnIdle { harness.owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(listOf("reply-1")))
    }

    @Test
    fun aReplyArrivingWhileScrolledUpIsMarkedOnlyOnceTheBottomIsReached() {
        val harness = Harness()
        harness.nearBottom = false
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(emptyList()))

        harness.nearBottom = true
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(listOf("reply-1")))
    }

    @Test
    fun aNewerReplyAtTheBottomIsMarkedAsItLands() {
        val harness = Harness()
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()

        harness.latestReplyId = "reply-2"
        composeRule.waitForIdle()

        assertThat(harness.marked, equalTo(listOf("reply-1", "reply-2")))
    }

    @Test
    fun returningToTheBottomRequestsTheSameReplyAgain() {
        val harness = Harness()
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()

        harness.nearBottom = false
        composeRule.waitForIdle()
        harness.nearBottom = true
        composeRule.waitForIdle()

        assertThat(harness.marked, equalTo(listOf("reply-1", "reply-1")))
    }

    @Test
    fun aReplacementLifecycleOwnerThatIsNotStartedHoldsTheGate() {
        val harness = Harness()
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()

        harness.owner = TestLifecycleOwner(initialState = Lifecycle.State.CREATED)
        harness.latestReplyId = "reply-2"
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(listOf("reply-1")))

        composeRule.runOnIdle { harness.owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        composeRule.waitForIdle()
        assertThat(harness.marked, equalTo(listOf("reply-1", "reply-2")))
    }

    @Test
    fun aReplyIdThatClearsAndReturnsIsRequestedAgain() {
        val harness = Harness()
        harness.latestReplyId = "reply-1"
        compose(harness)
        composeRule.waitForIdle()

        harness.latestReplyId = null
        composeRule.waitForIdle()
        harness.latestReplyId = "reply-1"
        composeRule.waitForIdle()

        assertThat(harness.marked, equalTo(listOf("reply-1", "reply-1")))
    }

    @Test
    fun nothingIsMarkedWithoutAReply() {
        val harness = Harness()
        compose(harness)
        composeRule.waitForIdle()

        assertThat(harness.marked, equalTo(emptyList()))
    }
}
