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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.AndroidDeliveredHubNotificationClearer
import io.rover.sdk.notifications.communicationhub.HubPushKind
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationReplyReceiverTest {

    @Test
    fun doesNotCrashWhenConversationIdIsMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = ConversationReplyReceiver()
        val intent = Intent(context, ConversationReplyReceiver::class.java)
        // No EXTRA_CONVERSATION_ID — should log a warning and return safely.
        // goAsync() will return a real PendingResult in Robolectric; finish() is called.
        receiver.onReceive(context, intent)
        // If we reach here without an exception, the guard works.
    }

    @Test
    fun doesNotCrashWhenReplyTextIsMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = ConversationReplyReceiver()
        val intent = Intent(context, ConversationReplyReceiver::class.java).apply {
            putExtra(ConversationReplyReceiver.EXTRA_CONVERSATION_ID, "conversation-1")
            // No RemoteInput result bundle attached.
        }
        receiver.onReceive(context, intent)
        // If we reach here without an exception, the guard works.
    }

    /**
     * Regression guard: the notification reposted after a successful inline reply replaces the
     * conversation's active notification, and must keep its Hub marker so a 410 reset still clears
     * it. Before the fix the repost dropped [HubPushNotification.EXTRA_HUB_PUSH_KIND] and the
     * reply notification survived the reset as a stale, tappable item.
     */
    @Test
    fun repostedReplyNotificationCarriesHubMarkerAndIsClearedOnReset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val channelId = "reply-channel"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Reply", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(" ").build())
            .addMessage("Thanks!", System.currentTimeMillis(), null as Person?)
        val notification = ConversationReplyReceiver.buildRepostedReplyNotification(
            context = context,
            channelId = channelId,
            smallIconResId = android.R.drawable.ic_dialog_info,
            style = style,
        )

        // The repost is identifiable as a Hub conversation notification.
        assertThat(HubPushNotification.kindOf(notification), equalTo(HubPushKind.CONVERSATION))

        // And a 410 reset clearer removes it from the tray.
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(
            "conversation-1",
            AndroidConversationPushNotificationPresenter.NOTIFICATION_INT_ID,
            notification,
        )
        assertThat(notificationManager.activeNotifications.size, equalTo(1))

        AndroidDeliveredHubNotificationClearer(context).clearDeliveredHubNotifications()

        assertThat(notificationManager.activeNotifications.firstOrNull(), absent())
    }
}
