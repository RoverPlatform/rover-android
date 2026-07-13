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

package io.rover.sdk.notifications.communicationhub.messages

import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import java.util.Locale

internal sealed class MessageFeedRow {
    abstract val timestamp: Long

    data class Post(
        val postWithSubscription: PostWithSubscription,
    ) : MessageFeedRow() {
        override val timestamp: Long = postWithSubscription.post.receivedAt
    }

    data class Conversation(
        val id: String,
        val senderName: String?,
        val senderAvatarUrl: String?,
        val subject: String?,
        val preview: String,
        override val timestamp: Long,
        val isUnread: Boolean,
    ) : MessageFeedRow()
}

internal object MessagesFeedBuilder {
    fun buildRows(
        posts: List<PostWithSubscription>,
        conversations: List<ConversationEntity>,
        participants: List<ParticipantEntity>,
        replies: List<ReplyEntity>,
        searchQuery: String,
    ): List<MessageFeedRow> {
        val participantsById = participants.associateBy { it.id }
        val latestReplyByConversation = replies
            .groupBy { it.conversationID }
            .mapValues { (_, conversationReplies) ->
                conversationReplies.maxByOrNull { it.createdAt }
            }

        val postRows = posts.map { MessageFeedRow.Post(it) }
        val conversationRows = conversations.map { conversation ->
            val latestReply = latestReplyByConversation[conversation.id]
            val displayParticipant = conversation.lastIncomingParticipantID
                ?.let(participantsById::get)
            val preview = resolvePreviewText(latestReply)
                .ifBlank { conversation.lastReplyPreview?.trim().orEmpty() }
            val unreadTimestamp = conversation.lastIncomingReplyAt ?: conversation.lastReplyAt
            val lastReadAt = conversation.lastReadAt
            val isUnread = lastReadAt == null || unreadTimestamp > lastReadAt

            MessageFeedRow.Conversation(
                id = conversation.id,
                senderName = displayParticipant?.name?.trim()?.ifBlank { null },
                senderAvatarUrl = displayParticipant?.avatarUrl?.trim()?.ifBlank { null },
                subject = conversation.subject?.trim()?.ifBlank { null },
                preview = preview,
                timestamp = latestReply?.createdAt ?: conversation.lastReplyAt,
                isUnread = isUnread,
            )
        }

        val allRows = (postRows + conversationRows).sortedByDescending { it.timestamp }
        return filterRows(allRows, searchQuery)
    }

    private fun filterRows(rows: List<MessageFeedRow>, query: String): List<MessageFeedRow> {
        if (query.isBlank()) return rows

        val loweredQuery = query.lowercase(Locale.getDefault())
        return rows.filter { row ->
            when (row) {
                is MessageFeedRow.Post -> {
                    row.postWithSubscription.post.subject.lowercase(Locale.getDefault()).contains(loweredQuery) ||
                        row.postWithSubscription.post.previewText.lowercase(Locale.getDefault()).contains(loweredQuery) ||
                        row.postWithSubscription.subscription?.name
                            ?.lowercase(Locale.getDefault())
                            ?.contains(loweredQuery) == true
                }

                is MessageFeedRow.Conversation -> {
                    row.senderName?.lowercase(Locale.getDefault())?.contains(loweredQuery) == true ||
                        row.subject?.lowercase(Locale.getDefault())?.contains(loweredQuery) == true ||
                        row.preview.lowercase(Locale.getDefault()).contains(loweredQuery)
                }
            }
        }
    }

    private fun resolvePreviewText(reply: ReplyEntity?): String {
        if (reply == null) return ""

        reply.content.forEach { block ->
            if (block.type.equals(ReplyContentBlock.TYPE_TEXT, ignoreCase = true) && !block.text.isNullOrBlank()) {
                return block.text
            }
        }

        if (reply.content.any { it.type.equals(ReplyContentBlock.TYPE_IMAGE, ignoreCase = true) }) {
            return "Image"
        }

        return ""
    }

}
