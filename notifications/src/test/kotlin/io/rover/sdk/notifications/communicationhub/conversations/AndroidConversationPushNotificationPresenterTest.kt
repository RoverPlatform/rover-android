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

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigationState
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidConversationPushNotificationPresenterTest {

    private fun makePresenter(
        context: Context,
        hubCoordinator: HubCoordinator = HubCoordinator(),
        standaloneConversationVisibilityTracker: StandaloneConversationVisibilityTracker =
            StandaloneConversationVisibilityTracker(),
    ): AndroidConversationPushNotificationPresenter {
        return AndroidConversationPushNotificationPresenter(
            applicationContext = context,
            smallIconResId = android.R.drawable.ic_dialog_info,
            smallIconDrawableLevel = 0,
            defaultChannelId = "conversation-channel",
            iconColor = 0xFF009966.toInt(),
            hubCoordinator = hubCoordinator,
            standaloneConversationVisibilityTracker = standaloneConversationVisibilityTracker,
        )
    }

    @Test
    fun notificationIsTaggedWithConversationId() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "Last preview",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications
        assertThat(notifications.size, equalTo(1))
        // Tag must be the stable conversationId, not conversationId:replyId.
        assertThat(notifications.first().tag, equalTo("conversation-1"))
        assertThat(notifications.first().id, equalTo(AndroidConversationPushNotificationPresenter.NOTIFICATION_INT_ID))
    }

    @Test
    fun notificationUsesMessagingStyleWithParticipantMessage() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "Last preview",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = nm.activeNotifications.first().notification

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        assertThat(style == null, equalTo(false))
        assertThat(style!!.messages.size, equalTo(1))
        assertThat(style.messages.first().text.toString(), equalTo("Last preview"))
        assertThat(style.messages.first().person?.name?.toString(), equalTo("Casey Jones"))
    }

    @Test
    fun secondPushForSameConversationAccumulatesMessages() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "First message",
        )
        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "Second message",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Still one notification (same conversationId tag).
        assertThat(nm.activeNotifications.size, equalTo(1))
        val notification = nm.activeNotifications.first().notification
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)!!
        assertThat(style.messages.size, equalTo(2))
        assertThat(style.messages[0].text.toString(), equalTo("First message"))
        assertThat(style.messages[1].text.toString(), equalTo("Second message"))
    }

    @Test
    fun notificationHasReplyAction() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "Last preview",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = nm.activeNotifications.first().notification
        assertThat(notification.actions == null, equalTo(false))
        assertThat(notification.actions.size, equalTo(1))
        assertThat(notification.actions.first().title.toString(), equalTo("Reply"))
        assertThat(notification.actions.first().remoteInputs == null, equalTo(false))
        assertThat(notification.actions.first().remoteInputs.size, equalTo(1))
        assertThat(
            notification.actions.first().remoteInputs.first().resultKey,
            equalTo(AndroidConversationPushNotificationPresenter.REMOTE_INPUT_KEY),
        )
    }

    @Test
    fun notificationChannelIsCreated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.getNotificationChannel("conversation-channel") == null, equalTo(false))
    }

    @Test
    fun contentIntentOpensShowConversationDeepLinkActivityWithConversationId() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = nm.activeNotifications.first().notification
        assertThat(notification.contentIntent == null, equalTo(false))
        val savedIntent = org.robolectric.Shadows.shadowOf(notification.contentIntent).savedIntent
        assertThat(
            savedIntent.component?.className,
            equalTo("io.rover.sdk.notifications.communicationhub.ui.ShowConversationActivity"),
        )
        assertThat(savedIntent.getStringExtra("conversation_id"), equalTo("conversation-1"))
    }

    @Test
    fun suppressesNotificationWhenHubIsShowingSameConversation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hubCoordinator = HubCoordinator().apply {
            updateNavigationVisibility(
                HubNavigationState.ShowingConversation("conversation-1"),
                isVisible = true,
            )
        }
        val presenter = makePresenter(context, hubCoordinator = hubCoordinator)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(0))
    }

    @Test
    fun doesNotSuppressNotificationWhenHubIsShowingDifferentConversation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hubCoordinator = HubCoordinator().apply {
            updateNavigationVisibility(
                HubNavigationState.ShowingConversation("conversation-2"),
                isVisible = true,
            )
        }
        val presenter = makePresenter(context, hubCoordinator = hubCoordinator)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(1))
    }

    @Test
    fun suppressesNotificationWhenStandaloneActivityIsShowingSameConversation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val standaloneConversationVisibilityTracker = StandaloneConversationVisibilityTracker().apply {
            updateConversationVisibility("conversation-1", isVisible = true)
        }
        val presenter = makePresenter(
            context,
            standaloneConversationVisibilityTracker = standaloneConversationVisibilityTracker,
        )

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(0))
    }

    @Test
    fun clearRemovesMatchingConversationNotification() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "body",
        )
        presenter.clearConversationNotification("conversation-1")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(0))
    }

    @Test
    fun clearDoesNotRemoveDifferentConversationNotification() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "first",
        )
        presenter.presentConversationNotification(
            conversationId = "conversation-2",
            participantName = "Taylor Swift",
            participantAvatarUrl = null,
            body = "second",
        )

        presenter.clearConversationNotification("conversation-1")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(1))
        assertThat(nm.activeNotifications.first().tag, equalTo("conversation-2"))
    }

    @Test
    fun clearWhenNoNotificationExistsIsNoOp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = makePresenter(context)

        presenter.clearConversationNotification("conversation-1")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(nm.activeNotifications.size, equalTo(0))
    }
}
