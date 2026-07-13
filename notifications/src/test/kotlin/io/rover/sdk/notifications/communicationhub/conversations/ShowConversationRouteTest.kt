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

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig
import io.rover.sdk.core.platform.SharedPreferencesLocalStorage
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.ui.ShowConversationActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowConversationRouteTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun makeRoute(deepLink: String?, isInboxEnabled: Boolean = true): Pair<ShowConversationRoute, HubCoordinator> {
        val hubCoordinator = HubCoordinator()
        val configManager = ConfigManager(SharedPreferencesLocalStorage(context))
        configManager.updateFromBackend(
            RoverConfig(
                hub = HubConfig(
                    isInboxEnabled = isInboxEnabled,
                    deepLink = deepLink,
                )
            )
        )
        val route = ShowConversationRoute(
            context = context,
            urlSchemes = setOf("rv-myapp"),
            hubCoordinator = hubCoordinator,
            configManager = configManager,
        )
        return route to hubCoordinator
    }

    @Test
    fun hubConfiguredPathReturnsActionViewIntentAndCallsNavigateToConversation() {
        val (route, hubCoordinator) = makeRoute(deepLink = "myapp://hub")

        val intent = route.resolveUri(URI("rv-myapp://conversations/conversation-123"))

        assertThat(intent?.action, equalTo(Intent.ACTION_VIEW))
        assertThat(intent?.dataString, equalTo("myapp://hub"))
        assertThat(
            hubCoordinator.pendingNavigation.value,
            equalTo(HubNavigation.Conversation("conversation-123"))
        )
    }

    @Test
    fun noHubDeepLinkPathReturnsShowConversationActivityIntentAndCallsNavigateToConversation() {
        val (route, hubCoordinator) = makeRoute(deepLink = null)

        val intent = route.resolveUri(URI("rv-myapp://conversations/conversation-456"))

        assertThat(intent?.component?.className, equalTo(ShowConversationActivity::class.java.name))
        assertThat(intent?.getStringExtra("conversation_id"), equalTo("conversation-456"))
        assertThat(
            hubCoordinator.pendingNavigation.value,
            equalTo(HubNavigation.Conversation("conversation-456"))
        )
    }

    @Test
    fun doesNotResolveWhenPathIsMissingConversationId() {
        val (route, _) = makeRoute(deepLink = "myapp://hub")

        val intent = route.resolveUri(URI("rv-myapp://conversations"))

        assertThat(intent, equalTo(null))
    }

    @Test
    fun doesNotResolveForUnknownAuthority() {
        val (route, _) = makeRoute(deepLink = "myapp://hub")

        val intent = route.resolveUri(URI("rv-myapp://posts/conversation-123"))

        assertThat(intent, equalTo(null))
    }

    @Test
    fun inboxDisabled_withDeepLinkSet_returnsShowConversationActivityNotActionView() {
        val (route, hubCoordinator) = makeRoute(deepLink = "myapp://hub", isInboxEnabled = false)

        val intent = route.resolveUri(URI("rv-myapp://conversations/conversation-789"))

        assertThat(intent?.component?.className, equalTo(ShowConversationActivity::class.java.name))
        assertThat(intent?.getStringExtra("conversation_id"), equalTo("conversation-789"))
        assertThat(
            hubCoordinator.pendingNavigation.value,
            equalTo(HubNavigation.Conversation("conversation-789"))
        )
    }
}
