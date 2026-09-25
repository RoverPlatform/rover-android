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
import io.rover.sdk.notifications.communicationhub.conversations.isUnread
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import java.text.Collator
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
        val participantIDs: List<String> = emptyList(),
    ) : MessageFeedRow()
}

internal object MessagesFeedBuilder {
    fun buildRows(
        posts: List<PostWithSubscription>,
        conversations: List<ConversationEntity>,
        participants: List<ParticipantEntity>,
        replies: List<ReplyEntity>,
        searchQuery: String,
        searchToken: HubSearchToken? = null,
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

            MessageFeedRow.Conversation(
                id = conversation.id,
                senderName = displayParticipant?.name?.trim()?.ifBlank { null },
                senderAvatarUrl = displayParticipant?.avatarUrl?.trim()?.ifBlank { null },
                subject = conversation.subject?.trim()?.ifBlank { null },
                preview = preview,
                timestamp = latestReply?.createdAt ?: conversation.lastReplyAt,
                isUnread = conversation.isUnread,
                participantIDs = conversation.participantIDs.orEmpty(),
            )
        }

        val allRows = (postRows + conversationRows).sortedByDescending { it.timestamp }
        return filterRows(allRows, searchQuery, searchToken)
    }

    /**
     * Builds token suggestions from the currently loaded items, so every
     * offered token is guaranteed to produce at least one result. Candidates
     * with blank ids or blank names (placeholder subscriptions that have not
     * synced yet) are skipped. Subscriptions are ordered before senders;
     * within each group names sort locale-aware with an id tie-break for
     * determinism.
     */
    fun buildSuggestions(
        posts: List<PostWithSubscription>,
        conversations: List<ConversationEntity>,
        participants: List<ParticipantEntity>,
        searchQuery: String,
    ): List<HubSearchToken> {
        if (searchQuery.isBlank()) return emptyList()

        val loweredQuery = searchQuery.lowercase(Locale.getDefault())
        fun String.matchesQuery() = lowercase(Locale.getDefault()).contains(loweredQuery)

        val subscriptionTokensById = mutableMapOf<String, HubSearchToken.Subscription>()
        posts.forEach { postWithSubscription ->
            val subscription = postWithSubscription.subscription ?: return@forEach
            if (subscription.id.isBlank() || subscription.name.isBlank()) return@forEach
            if (!subscription.name.matchesQuery()) return@forEach
            subscriptionTokensById[subscription.id] = HubSearchToken.Subscription(
                id = subscription.id,
                name = subscription.name,
                imageUrl = subscription.logoURL?.trim()?.ifBlank { null },
            )
        }

        val participantsById = participants.associateBy { it.id }
        val senderTokensById = mutableMapOf<String, HubSearchToken.Sender>()
        conversations.forEach { conversation ->
            conversation.participantIDs.orEmpty().forEach participant@{ participantId ->
                val participant = participantsById[participantId] ?: return@participant
                val name = participant.name?.trim().orEmpty()
                if (participant.id.isBlank() || name.isBlank()) return@participant
                if (!name.matchesQuery()) return@participant
                senderTokensById[participant.id] = HubSearchToken.Sender(
                    participantID = participant.id,
                    name = name,
                    imageUrl = participant.avatarUrl?.trim()?.ifBlank { null },
                )
            }
        }

        val collator = Collator.getInstance()
        val subscriptionTokens = subscriptionTokensById.values
            .sortedWith(compareBy(collator, HubSearchToken.Subscription::name).thenBy(HubSearchToken.Subscription::id))
        val senderTokens = senderTokensById.values
            .sortedWith(compareBy(collator, HubSearchToken.Sender::name).thenBy(HubSearchToken.Sender::participantID))
        return subscriptionTokens + senderTokens
    }

    private fun filterRows(
        rows: List<MessageFeedRow>,
        query: String,
        token: HubSearchToken?,
    ): List<MessageFeedRow> {
        if (token == null && query.isBlank()) return rows

        val loweredQuery = query.lowercase(Locale.getDefault())
        return rows.filter { row ->
            when (token) {
                null -> matchesFreeText(row, loweredQuery)

                is HubSearchToken.Subscription ->
                    row is MessageFeedRow.Post &&
                        row.postWithSubscription.post.subscriptionId == token.id &&
                        (query.isBlank() || matchesContentFields(row, loweredQuery))

                is HubSearchToken.Sender ->
                    row is MessageFeedRow.Conversation &&
                        token.participantID in row.participantIDs &&
                        (query.isBlank() || matchesContentFields(row, loweredQuery))
            }
        }
    }

    private fun matchesFreeText(row: MessageFeedRow, loweredQuery: String): Boolean {
        return when (row) {
            is MessageFeedRow.Post -> {
                matchesContentFields(row, loweredQuery) ||
                    row.postWithSubscription.subscription?.name
                        ?.lowercase(Locale.getDefault())
                        ?.contains(loweredQuery) == true
            }

            is MessageFeedRow.Conversation -> {
                matchesContentFields(row, loweredQuery) ||
                    row.senderName?.lowercase(Locale.getDefault())?.contains(loweredQuery) == true
            }
        }
    }

    /**
     * Content-only matching used within a token's scope: subject and preview,
     * never the subscription or sender name — otherwise typing part of the
     * token's own name would match everything in scope.
     */
    private fun matchesContentFields(row: MessageFeedRow, loweredQuery: String): Boolean {
        return when (row) {
            is MessageFeedRow.Post -> {
                row.postWithSubscription.post.subject.lowercase(Locale.getDefault()).contains(loweredQuery) ||
                    row.postWithSubscription.post.previewText.lowercase(Locale.getDefault()).contains(loweredQuery)
            }

            is MessageFeedRow.Conversation -> {
                row.subject?.lowercase(Locale.getDefault())?.contains(loweredQuery) == true ||
                    row.preview.lowercase(Locale.getDefault()).contains(loweredQuery)
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
