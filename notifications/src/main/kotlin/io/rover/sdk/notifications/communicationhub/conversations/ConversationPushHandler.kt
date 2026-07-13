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

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.conversations.dto.ConversationItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ParticipantItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal interface ConversationPushRepository {
    suspend fun saveConversationPushPayload(
        conversation: ConversationItem,
        reply: ReplyItem,
        participant: ParticipantItem,
    )
}

internal interface ConversationPushNotificationPresenter {
    val smallIconResId: Int

    suspend fun presentConversationNotification(
        conversationId: String,
        participantName: String?,
        participantAvatarUrl: String?,
        body: String,
    )

    suspend fun clearConversationNotification(conversationId: String)
}

internal class ConversationPushHandler(
    private val conversationPushRepository: ConversationPushRepository,
    private val notificationPresenter: ConversationPushNotificationPresenter,
    private val coroutineScope: CoroutineScope,
) {
    private val pushPayloadAdapter = Moshi.Builder()
        .build()
        .adapter(ConversationPushPayload::class.java)

    fun handleCommunicationHubPush(roverJson: String) {
        val payload = try {
            pushPayloadAdapter.fromJson(roverJson)
        } catch (e: Exception) {
            log.w("Invalid conversation push received: '${e.message}'")
            null
        } ?: return

        coroutineScope.launch {
            try {
                conversationPushRepository.saveConversationPushPayload(
                    conversation = payload.conversation,
                    reply = payload.reply,
                    participant = payload.participant,
                )

                val body = payload.conversation.lastReplyPreview
                    ?.trim()
                    ?.ifBlank { null }
                    ?: resolveReplyPreview(payload.reply)

                notificationPresenter.presentConversationNotification(
                    conversationId = payload.conversation.id,
                    participantName = payload.participant.name,
                    participantAvatarUrl = payload.participant.avatarURL,
                    body = body,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("Failed to process conversation push: ${e.message}")
            }
        }
    }

    private fun resolveReplyPreview(reply: ReplyItem): String {
        reply.content.forEach { block ->
            if (block.type.equals("text", ignoreCase = true) && !block.text.isNullOrBlank()) {
                return block.text
            }
        }

        if (reply.content.any { it.type.equals("image", ignoreCase = true) }) {
            return "Image"
        }

        return ""
    }
}

@JsonClass(generateAdapter = true)
internal data class ConversationPushPayload(
    val conversation: ConversationItem,
    val reply: ReplyItem,
    val participant: ParticipantItem,
)
