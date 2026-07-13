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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.Test

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
}
