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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.HubPushKind
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import io.rover.sdk.notifications.conversationsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class ConversationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = try {
            goAsync()
        } catch (e: Exception) {
            log.w("ConversationReplyReceiver: goAsync() failed: ${e.message}")
            return
        }

        // In some test environments, goAsync() may return null
        if (pendingResult == null) {
            log.w("ConversationReplyReceiver: goAsync() returned null, ignoring.")
            return
        }

        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (conversationId.isNullOrBlank()) {
            log.w("ConversationReplyReceiver: missing conversationId, ignoring.")
            pendingResult.finish()
            return
        }

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getString(AndroidConversationPushNotificationPresenter.REMOTE_INPUT_KEY)
            ?.trim()
            ?.ifBlank { null }
        if (replyText == null) {
            log.w("ConversationReplyReceiver: empty reply text, ignoring.")
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Rover.shared.conversationsRepository.sendReply(
                    conversationId = conversationId,
                    message = replyText,
                )

                // Append the sent message to the MessagingStyle so the notification
                // immediately reflects the reply (null sender = "me").
                val nm = NotificationManagerCompat.from(context)
                val existing = nm.activeNotifications
                    .firstOrNull { it.tag == conversationId && it.id == AndroidConversationPushNotificationPresenter.NOTIFICATION_INT_ID }
                val style = existing?.let {
                    NotificationCompat.MessagingStyle
                        .extractMessagingStyleFromNotification(it.notification)
                } ?: NotificationCompat.MessagingStyle(Person.Builder().setName(" ").build())
                style.addMessage(replyText, System.currentTimeMillis(), null as Person?)

                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
                    ?: existing?.notification?.channelId
                    ?: "rover"
                val smallIconResId = intent.getIntExtra(EXTRA_SMALL_ICON_RES_ID, 0)
                    .takeIf { it != 0 }
                    ?: runCatching {
                        Rover.shared.resolve(ConversationPushNotificationPresenter::class.java)?.smallIconResId
                    }.getOrNull()
                    ?: 0
                val notification = buildRepostedReplyNotification(
                    context = context,
                    channelId = channelId,
                    smallIconResId = smallIconResId,
                    style = style,
                )
                nm.notify(conversationId, AndroidConversationPushNotificationPresenter.NOTIFICATION_INT_ID, notification)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("ConversationReplyReceiver: failed to send reply: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_SMALL_ICON_RES_ID = "small_icon_res_id"

        /**
         * Builds the notification reposted after a successful inline reply.
         *
         * This repost *replaces* the conversation's active notification, so it must re-stamp the
         * Hub marker: without it the reply notification would lose its [HubPushNotification]
         * [HubPushKind.CONVERSATION] kind and survive a 410 reset that is meant to clear it.
         * Extracted so the stamping is exercised directly by tests.
         */
        internal fun buildRepostedReplyNotification(
            context: Context,
            channelId: String,
            smallIconResId: Int,
            style: NotificationCompat.MessagingStyle,
        ): android.app.Notification {
            val builder = NotificationCompat.Builder(context, channelId)
                .setStyle(style)
                .apply {
                    if (smallIconResId != 0) {
                        setSmallIcon(smallIconResId)
                    }
                }
            HubPushNotification.stamp(builder, HubPushKind.CONVERSATION)
            return builder.build()
        }
    }
}
