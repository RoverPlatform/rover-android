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

package io.rover.sdk.notifications

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.Rover
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.platform.DateFormatting
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.SharedPreferencesLocalStorage
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.ui.ShowConversationActivity
import io.rover.sdk.notifications.ui.concerns.NotificationStoreInterface
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotificationTapTestApplication::class)
class ConversationNotificationTapActivityTest {

    @Test
    fun conversationNotificationWithUnresolvableHubDeepLinkFallsBackWithoutFinishing() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val intent = ShowConversationActivity.makeIntent(
            context = application,
            uri = null,
            conversationId = "conversation-1",
        )

        val controller = Robolectric.buildActivity(ShowConversationActivity::class.java, intent)
            .create()

        assertThat(controller.get().isFinishing, equalTo(false))
        // Nothing consumed the queued navigation, so it must not be left behind for the Hub.
        val hubCoordinator = Rover.shared.resolveSingletonOrFail(HubCoordinator::class.java)
        assertThat(hubCoordinator.pendingNavigation.value, absent())

        // The standalone fallback hosts ConversationDetailViewModel, whose viewModelScope runs on
        // Dispatchers.Main. Destroy the activity so its ViewModelStore is cleared; otherwise those
        // coroutines outlive this test and collide with tests that swap Main via setMain.
        controller.destroy()
    }

    @Test
    fun conversationNotificationWithResolvableHubDeepLinkRedirectsToHub() {
        val application = ApplicationProvider.getApplicationContext<Context>()

        // Give the configured hub deep link a handler, so the coordination path is taken.
        val hubActivity = ComponentName(application.packageName, "TestHubActivity")
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(hubActivity)
            addIntentFilterForActivity(
                hubActivity,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("unhandled-test-scheme")
                    addDataAuthority("hub", null)
                }
            )
        }

        val intent = ShowConversationActivity.makeIntent(
            context = application,
            uri = null,
            conversationId = "conversation-2",
        )

        val controller = Robolectric.buildActivity(ShowConversationActivity::class.java, intent)
            .create()

        assertThat(controller.get().isFinishing, equalTo(true))
        // Assert the hub intent was really dispatched; finishing alone would also be true if the
        // activity had silently dropped it.
        val started = shadowOf(application as Application).nextStartedActivity
        assertThat(started?.action, equalTo(Intent.ACTION_VIEW))
        assertThat(started?.dataString, equalTo("unhandled-test-scheme://hub"))
        assertThat(started?.`package`, equalTo(application.packageName))
        val hubCoordinator = Rover.shared.resolveSingletonOrFail(HubCoordinator::class.java)
        assertThat(
            hubCoordinator.pendingNavigation.value,
            equalTo(HubNavigation.Conversation("conversation-2") as HubNavigation)
        )
        controller.destroy()
    }

    // The ActivityNotFoundException catches in ShowConversationActivity and
    // TransientNotificationLaunchActivity cannot be driven from here: Robolectric resolves the
    // HubDeepLink check and the launch against the same shadow PackageManager.
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NotificationTapTestApplication::class)
class ClassicNotificationTapActivityTest {

    @Test
    fun malformedClassicNotificationFinishesWithoutThrowing() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(application, TransientNotificationLaunchActivity::class.java).apply {
            putExtra("notification_json", "not-json")
        }

        val controller = Robolectric.buildActivity(TransientNotificationLaunchActivity::class.java, intent)
            .create()
            .start()

        assertThat(controller.get().isFinishing, equalTo(true))
        // Finishing alone would also hold for any early return, so pin down that this exit came
        // from the decode guard: the influence tracker sits past it, the read marker never had a
        // notification to record, and nothing was launched.
        verify(Rover.shared.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java), atLeastOnce())
            .notificationOpenedDirectly()
        verify(Rover.shared.resolveSingletonOrFail(NotificationStoreInterface::class.java), never())
            .markRead(any())
        assertThat(shadowOf(application as Application).nextStartedActivity, absent())
        controller.destroy()
    }

}

class NotificationTapTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Rover.failableShared != null) return

        val configManager = ConfigManager(SharedPreferencesLocalStorage(this))
        configManager.updateFromBackend(
            RoverConfig(
                hub = HubConfig(
                    isInboxEnabled = true,
                    deepLink = "unhandled-test-scheme://hub",
                )
            )
        )

        Rover.initialize(
            object : Assembler {
                override fun assemble(container: Container) {
                    container.register(Scope.Singleton, ConfigManager::class.java) { _ ->
                        configManager
                    }
                    container.register(Scope.Singleton, HubCoordinator::class.java) { _ ->
                        HubCoordinator()
                    }
                    container.register(Scope.Singleton, DateFormattingInterface::class.java) { _ ->
                        DateFormatting()
                    }
                    container.register(Scope.Singleton, NotificationOpenInterface::class.java) { resolver ->
                        NotificationOpen(
                            applicationContext = this@NotificationTapTestApplication,
                            dateFormatting = resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                            eventsService = mock<EventQueueServiceInterface>(),
                            router = mock<Router>(),
                            openAppIntent = null,
                            embeddedWebBrowserDisplay = mock<EmbeddedWebBrowserDisplayInterface>(),
                        )
                    }
                    container.register(Scope.Singleton, InfluenceTrackerServiceInterface::class.java) { _ ->
                        mock<InfluenceTrackerServiceInterface>()
                    }
                    container.register(Scope.Singleton, NotificationStoreInterface::class.java) { _ ->
                        mock<NotificationStoreInterface>()
                    }
                }
            }
        )
    }
}
