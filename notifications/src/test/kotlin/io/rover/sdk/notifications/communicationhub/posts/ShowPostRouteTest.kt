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

package io.rover.sdk.notifications.communicationhub.posts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig
import io.rover.sdk.core.platform.SharedPreferencesLocalStorage
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.ui.ShowPostActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowPostRouteTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun unresolvableHubDeepLinkFallsBackToStandalonePost() {
        val (route, hubCoordinator) = makeRoute(deepLink = "unhandled-test-scheme://hub")

        val intent = route.resolveUri(URI("rv-myapp://posts/post-123"))

        assertThat(intent?.component?.className, equalTo(ShowPostActivity::class.java.name))
        assertThat(intent?.getStringExtra("post_id"), equalTo("post-123"))
        // The standalone activity never consumes pending navigation, so queuing it here would
        // make the Hub jump to an already-read post the next time it is presented.
        assertThat(hubCoordinator.pendingNavigation.value, absent())
    }

    @Test
    fun blankHubDeepLinkFallsBackToStandalonePost() {
        val (route, hubCoordinator) = makeRoute(deepLink = "")

        val intent = route.resolveUri(URI("rv-myapp://posts/post-blank-link"))

        assertThat(intent?.component?.className, equalTo(ShowPostActivity::class.java.name))
        assertThat(intent?.getStringExtra("post_id"), equalTo("post-blank-link"))
        assertThat(hubCoordinator.pendingNavigation.value, absent())
    }

    @Test
    fun inboxDisabledFallsBackToStandalonePost() {
        val (route, hubCoordinator) = makeRoute(
            deepLink = "handled-test-scheme://hub",
            isInboxEnabled = false,
        )

        val intent = route.resolveUri(URI("rv-myapp://posts/post-no-inbox"))

        assertThat(intent?.component?.className, equalTo(ShowPostActivity::class.java.name))
        assertThat(hubCoordinator.pendingNavigation.value, absent())
    }

    @Test
    fun httpsHubDeepLinkHandledOnlyByAnotherPackageFallsBackToStandalonePost() {
        // The notifications manifest declares a <queries> entry for https browsers, so a web link
        // the host app does not itself handle still resolves. HubCoordinator is in-process, so a
        // handler in another package could never consume the queued navigation.
        val browser = ComponentName("com.example.browser", "BrowserActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(browser)
            addIntentFilterForActivity(
                browser,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("https")
                }
            )
        }
        val (route, hubCoordinator) = makeRoute(deepLink = "https://example.com/hub")

        val intent = route.resolveUri(URI("rv-myapp://posts/post-https"))

        assertThat(intent?.component?.className, equalTo(ShowPostActivity::class.java.name))
        assertThat(hubCoordinator.pendingNavigation.value, absent())
    }

    @Test
    fun resolvableHubDeepLinkReturnsActionViewIntent() {
        val hubDeepLink = "handled-test-scheme://hub"
        val hubActivity = ComponentName(context.packageName, "TestHubActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(hubActivity)
            addIntentFilterForActivity(
                hubActivity,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("handled-test-scheme")
                    addDataAuthority("hub", null)
                }
            )
        }
        val (route, hubCoordinator) = makeRoute(deepLink = hubDeepLink)

        val intent = route.resolveUri(URI("rv-myapp://posts/post-456"))

        assertThat(intent?.action, equalTo(Intent.ACTION_VIEW))
        assertThat(intent?.dataString, equalTo(hubDeepLink))
        assertThat(
            hubCoordinator.pendingNavigation.value,
            equalTo(HubNavigation.Post("post-456"))
        )
    }

    private fun makeRoute(
        deepLink: String,
        isInboxEnabled: Boolean = true,
    ): Pair<ShowPostRoute, HubCoordinator> {
        val configManager = ConfigManager(
            SharedPreferencesLocalStorage(context, "show-post-route-test")
        )
        configManager.updateFromBackend(
            RoverConfig(
                hub = HubConfig(
                    isInboxEnabled = isInboxEnabled,
                    deepLink = deepLink,
                )
            )
        )
        val hubCoordinator = HubCoordinator()
        return ShowPostRoute(
            context = context,
            urlSchemes = setOf("rv-myapp"),
            hubCoordinator = hubCoordinator,
            configManager = configManager,
        ) to hubCoordinator
    }
}
