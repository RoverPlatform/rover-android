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

package io.rover.sdk.notifications.communicationhub.navigation

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

/**
 * Covers the two concerns of [HubCoordinator]:
 *
 * - the informational visibility state;
 * - [HubCoordinator.clearPendingNavigation] taking a consumed value, which the Hub calls once it
 *   has executed a navigation, and which must not erase a request queued while it was executing.
 */
class HubCoordinatorTest {
    @Test
    fun visibleRouteIsPublished() {
        val hubCoordinator = HubCoordinator()

        hubCoordinator.updateNavigationVisibility(
            HubNavigationState.ShowingConversation("conversation-1"),
            isVisible = true,
        )

        assertThat(
            hubCoordinator.navigationState.value,
            equalTo(HubNavigationState.ShowingConversation("conversation-1")),
        )
    }

    @Test
    fun hiddenRouteClearsCurrentVisibleState() {
        val hubCoordinator = HubCoordinator()
        val state = HubNavigationState.ShowingConversation("conversation-1")

        hubCoordinator.updateNavigationVisibility(state, isVisible = true)
        hubCoordinator.updateNavigationVisibility(state, isVisible = false)

        assertThat(hubCoordinator.navigationState.value, equalTo(HubNavigationState.Hidden))
    }

    @Test
    fun staleHiddenCallbackDoesNotClearNewerVisibleRoute() {
        val hubCoordinator = HubCoordinator()
        val first = HubNavigationState.ShowingConversation("conversation-1")
        val second = HubNavigationState.ShowingConversation("conversation-2")

        hubCoordinator.updateNavigationVisibility(first, isVisible = true)
        hubCoordinator.updateNavigationVisibility(second, isVisible = true)
        hubCoordinator.updateNavigationVisibility(first, isVisible = false)

        assertThat(hubCoordinator.navigationState.value, equalTo(second))
    }

    @Test
    fun clearingAConsumedNavigationRemovesIt() {
        val coordinator = HubCoordinator()
        coordinator.navigateToInbox()

        coordinator.clearPendingNavigation(HubNavigation.Inbox)

        assertThat(coordinator.pendingNavigation.value, absent())
    }

    @Test
    fun clearingAConsumedNavigationDoesNotEraseANewerOne() {
        val coordinator = HubCoordinator()
        coordinator.navigateToPost("post-1")

        // The Hub consumed Post("post-1"), but before it clears, a newer navigation is queued
        // (e.g. from a background sync or a second push tap).
        coordinator.navigateToConversation("conv-1")
        coordinator.clearPendingNavigation(HubNavigation.Post("post-1"))

        assertThat(
            coordinator.pendingNavigation.value,
            equalTo<HubNavigation?>(HubNavigation.Conversation("conv-1")),
        )
    }

    @Test
    fun anIdenticalReRequestIsSatisfiedByTheConsumedNavigation() {
        val coordinator = HubCoordinator()
        coordinator.navigateToPost("post-1")

        // A second identical request (e.g. tapping the same push twice) is conflated by the
        // StateFlow and considered satisfied by the in-flight navigation to that destination,
        // so the consumed clear removes it rather than navigating to the same screen twice.
        coordinator.navigateToPost("post-1")
        coordinator.clearPendingNavigation(HubNavigation.Post("post-1"))

        assertThat(coordinator.pendingNavigation.value, absent())
    }
}
