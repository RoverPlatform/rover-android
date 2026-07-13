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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.rover.core.R
import io.rover.sdk.notifications.communicationhub.HubPushKind
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigationState
import io.rover.sdk.notifications.communicationhub.ui.ShowConversationActivity

internal class AndroidConversationPushNotificationPresenter(
    private val applicationContext: Context,
    @param:DrawableRes override val smallIconResId: Int,
    private val smallIconDrawableLevel: Int,
    private val defaultChannelId: String,
    private val iconColor: Int?,
    private val hubCoordinator: HubCoordinator,
    private val standaloneConversationVisibilityTracker: StandaloneConversationVisibilityTracker,
) : ConversationPushNotificationPresenter {
    override suspend fun clearConversationNotification(conversationId: String) {
        NotificationManagerCompat.from(applicationContext).cancel(conversationId, NOTIFICATION_INT_ID)
    }

    override suspend fun presentConversationNotification(
        conversationId: String,
        participantName: String?,
        participantAvatarUrl: String?,
        body: String,
    ) {
        if (isConversationVisible(conversationId)) return

        ensureDefaultChannelExists()

        // 1. Load avatar bitmap (suspend; null if URL absent or load fails).
        val bitmap: Bitmap? = participantAvatarUrl?.let { url ->
            try {
                val loader = ImageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(url)
                    .allowHardware(false) // required to extract a software Bitmap
                    .build()
                val result = loader.execute(request)
                (result as? SuccessResult)?.drawable?.let { drawable ->
                    (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }
            } catch (_: Exception) {
                null
            }
        }

        // 2. Build participant Person.
        val participantPerson = Person.Builder()
            .setName(participantName)
            .apply { bitmap?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        // 3. Accumulate MessagingStyle from any existing notification for this conversation.
        val nm = NotificationManagerCompat.from(applicationContext)
        val existing = nm.activeNotifications
            .firstOrNull { it.tag == conversationId && it.id == NOTIFICATION_INT_ID }
        val style = existing?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification)
        } ?: NotificationCompat.MessagingStyle(Person.Builder().setName(" ").build())
        style.addMessage(body, System.currentTimeMillis(), participantPerson)

        // 4. Build RemoteInput reply action.
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
            .setLabel("Reply")
            .build()
        val replyIntent = Intent(applicationContext, ConversationReplyReceiver::class.java).apply {
            putExtra(ConversationReplyReceiver.EXTRA_CONVERSATION_ID, conversationId)
            putExtra(ConversationReplyReceiver.EXTRA_CHANNEL_ID, defaultChannelId)
            putExtra(ConversationReplyReceiver.EXTRA_SMALL_ICON_RES_ID, smallIconResId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            conversationId.hashCode(),
            replyIntent,
            // FLAG_MUTABLE is required by RemoteInput — the system writes the reply text
            // back into the PendingIntent before delivery.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyAction = NotificationCompat.Action.Builder(0, "Reply", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        // 5. Build and post notification.
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(applicationContext, defaultChannelId)
        } else {
            @Suppress("DEPRECATION")
            NotificationCompat.Builder(applicationContext)
        }

        builder.setContentTitle(participantName)
        builder.setContentText(body)
        builder.setStyle(style)
        builder.setSmallIcon(smallIconResId, smallIconDrawableLevel)
        iconColor?.let { builder.color = it }
        builder.addAction(replyAction)

        // Mark this as a Hub conversation notification so a 410 Hub reset can clear it from the tray.
        HubPushNotification.stamp(builder, HubPushKind.CONVERSATION)

        val openIntent = ShowConversationActivity.makeIntent(
            context = applicationContext,
            uri = null,
            conversationId = conversationId,
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        builder.setContentIntent(
            PendingIntent.getActivity(applicationContext, conversationId.hashCode(), openIntent, flags)
        )

        nm.notify(
            conversationId,
            NOTIFICATION_INT_ID,
            builder.build().apply {
                this.flags = this.flags or Notification.FLAG_AUTO_CANCEL
            }
        )
    }

    private fun ensureDefaultChannelExists() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(defaultChannelId) != null) return

        val channelName = applicationContext.getString(R.string.default_notification_channel_name)
        val channelDescription = applicationContext.getString(R.string.default_notification_description)
        val channel = NotificationChannel(defaultChannelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = channelDescription
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_INT_ID = 0x524F5652
        const val REMOTE_INPUT_KEY = "reply_text"
    }

    private fun isConversationVisible(conversationId: String): Boolean {
        val hubState = hubCoordinator.navigationState.value
        if (hubState == HubNavigationState.ShowingConversation(conversationId)) {
            return true
        }

        return standaloneConversationVisibilityTracker.visibilityState.value ==
            StandaloneConversationVisibilityState.ShowingConversation(conversationId)
    }
}
