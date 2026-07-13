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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription


import org.junit.Test

class MessagesFeedBuilderTest {
    @Test
    fun mergesPostsAndConversationsSortedByNewestTimestamp() {
        val post = post(id = "post-1", receivedAt = 2000L)
        val conversation = conversation(
            id = "conversation-1",
            updatedAt = 1704067203000L,
        )
        val reply = reply(
            id = "reply-1",
            conversationID = "conversation-1",
            createdAt = 1704067203000L,
            participantID = null,
            text = "Recent conversation reply"
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = listOf(post),
            conversations = listOf(conversation),
            participants = emptyList(),
            replies = listOf(reply),
            searchQuery = ""
        )

        assertThat(rows.size, equalTo(2))
        assertThat((rows[0] as MessageFeedRow.Conversation).id, equalTo("conversation-1"))
        assertThat((rows[1] as MessageFeedRow.Post).postWithSubscription.post.id, equalTo("post-1"))
    }

    @Test
    fun conversationRowFallsBackToParticipantWhenSubjectMissingAndIgnoresMissingParticipantIds() {
        val conversation = conversation(
            id = "conversation-2",
            subject = "",
            updatedAt = iso(1000L),
            participantIDs = listOf("participant-1", "missing-participant"),
            lastIncomingParticipantID = "participant-1",
        )

        val participant = ParticipantEntity(
            id = "participant-1",
            name = "Sky Miller",
            avatarUrl = null,
            updatedAt = 1704067200000L,
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = listOf(participant),
            replies = emptyList(),
            searchQuery = ""
        )

        val row = rows.single() as MessageFeedRow.Conversation
        assertThat(row.senderName, equalTo("Sky Miller"))
        assertThat(row.subject, equalTo(null))
        assertThat(row.preview, equalTo(""))
    }

    @Test
    fun conversationRowHidesSenderWhenLastIncomingParticipantHasNotSyncedYet() {
        val conversation = conversation(
            id = "conversation-missing-participant",
            updatedAt = iso(1000L),
            lastIncomingParticipantID = "participant-missing",
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = emptyList(),
            replies = emptyList(),
            searchQuery = "",
        )

        val row = rows.single() as MessageFeedRow.Conversation
        assertThat(row.senderName, equalTo(null))
        assertThat(row.senderAvatarUrl, equalTo(null))
    }

    @Test
    fun conversationRowIsUnreadWhenLastIncomingReplyIsAfterLastRead() {
        val conversation = conversation(
            id = "conversation-3",
            lastIncomingReplyAt = iso(3000L),
            lastReadAt = iso(2000L),
            updatedAt = iso(1000L),
        )

        val me = ParticipantEntity(
            id = "participant-me",
            name = "Me Self",
            avatarUrl = null,
            updatedAt = 1704067200000L,
        )

        val other = ParticipantEntity(
            id = "participant-other",
            name = "Alex Rae",
            avatarUrl = null,
            updatedAt = 1704067200000L,
        )

        val olderMeReply = reply(
            id = "reply-older",
            conversationID = "conversation-3",
            createdAt = 1704067201000L,
            participantID = "participant-me",
            text = "Old"
        )
        val middleOtherReply = reply(
            id = "reply-latest",
            conversationID = "conversation-3",
            createdAt = 1704067203000L,
            participantID = "participant-other",
            text = "Incoming"
        )
        val latestMeReply = reply(
            id = "reply-my-latest",
            conversationID = "conversation-3",
            createdAt = 1704067204000L,
            participantID = "participant-me",
            text = "Latest"
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = listOf(me, other),
            replies = listOf(olderMeReply, middleOtherReply, latestMeReply),
            searchQuery = ""
        )

        val row = rows.single() as MessageFeedRow.Conversation
        assertThat(row.isUnread, equalTo(true))
        assertThat(row.preview, equalTo("Latest"))
    }

    @Test
    fun conversationRowIsReadWhenLastReadIsAtOrAfterLastIncomingReply() {
        val conversation = conversation(
            id = "conversation-4",
            lastIncomingReplyAt = iso(2000L),
            lastReadAt = iso(2000L),
            updatedAt = iso(1000L),
        )

        val other = ParticipantEntity(
            id = "participant-other",
            name = "Alex Rae",
            avatarUrl = null,
            updatedAt = 1704067200000L,
        )

        val otherReply = reply(
            id = "reply-other",
            conversationID = "conversation-4",
            createdAt = 1704067202000L,
            participantID = "participant-other",
            text = "Latest"
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = listOf(other),
            replies = listOf(otherReply),
            searchQuery = ""
        )

        val row = rows.single() as MessageFeedRow.Conversation
        assertThat(row.isUnread, equalTo(false))
    }

    @Test
    fun conversationRowFallsBackToLastReplyAtWhenIncomingTimestampMissing() {
        val conversation = conversation(
            id = "conversation-fallback",
            updatedAt = iso(3000L),
            lastReplyAt = iso(3000L),
            lastIncomingReplyAt = null,
            lastReadAt = iso(2000L),
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = emptyList(),
            replies = emptyList(),
            searchQuery = ""
        )

        val row = rows.single() as MessageFeedRow.Conversation
        assertThat(row.isUnread, equalTo(true))
    }

    @Test
    fun filtersRowsBySearchQueryAcrossConversationContent() {
        val post = post(id = "post-1", receivedAt = 1000L)
        val conversation = conversation(
            id = "conversation-5",
            updatedAt = 1704067202000L,
            lastIncomingParticipantID = "participant-1",
        )
        val participant = ParticipantEntity(
            id = "participant-1",
            name = "Alpha Sender",
            avatarUrl = "https://example.com/avatar.png",
            updatedAt = 1704067200000L,
        )
        val reply = reply(
            id = "reply-1",
            conversationID = "conversation-5",
            createdAt = 1704067202000L,
            participantID = null,
            text = "alpha term"
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = listOf(post),
            conversations = listOf(conversation),
            participants = listOf(participant),
            replies = listOf(reply),
            searchQuery = "alpha"
        )

        assertThat(rows.size, equalTo(1))
        assertThat((rows.single() as MessageFeedRow.Conversation).id, equalTo("conversation-5"))
    }

    @Test
    fun conversationRowPreviewFallsBackToImageLabelWhenReplyHasNoText() {
        val conversation = conversation(
            id = "conversation-6",
            updatedAt = 1704067201000L,
        )
        val imageOnlyReply = ReplyEntity(
            id = "reply-image",
            conversationID = "conversation-6",
            senderType = "fan",
            participantID = null,
            externalID = null,
            createdAt = iso(1000L),
            content = listOf(
                ReplyContentBlock(
                    type = ReplyContentBlock.TYPE_IMAGE,
                    text = null,
                    url = "https://example.com/image.png",
                )
            ),
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(conversation),
            participants = emptyList(),
            replies = listOf(imageOnlyReply),
            searchQuery = ""
        )

        assertThat((rows.single() as MessageFeedRow.Conversation).preview, equalTo("Image"))
    }

    @Test
    fun buildRowsSortsConversationUsingRawEpochReplyTimestamp() {
        val post = post(id = "post-epoch", receivedAt = 2000L)
        val conversation = conversation(
            id = "conversation-epoch",
            updatedAt = 1000L,
        )
        val reply = reply(
            id = "reply-epoch",
            conversationID = "conversation-epoch",
            createdAt = 3000L,
            participantID = null,
            text = "Epoch reply"
        )

        val rows = MessagesFeedBuilder.buildRows(
            posts = listOf(post),
            conversations = listOf(conversation),
            participants = emptyList(),
            replies = listOf(reply),
            searchQuery = ""
        )

        assertThat(rows.size, equalTo(2))
        assertThat((rows[0] as MessageFeedRow.Conversation).id, equalTo("conversation-epoch"))
        assertThat((rows[1] as MessageFeedRow.Post).postWithSubscription.post.id, equalTo("post-epoch"))
    }

    @Test
    fun replyHelperUsesFanSenderTypeForOutgoingReplies() {
        val reply = reply(
            id = "reply-1",
            conversationID = "conversation-1",
            createdAt = iso(1000L),
            participantID = null,
            text = "hello",
        )

        assertThat(reply.senderType, equalTo("fan"))
    }

    private fun post(id: String, receivedAt: Long): PostWithSubscription {
        return PostWithSubscription(
            post = PostEntity(
                id = id,
                subject = "Post $id",
                previewText = "Preview $id",
                receivedAt = receivedAt,
                url = "https://example.com/$id",
                isRead = false,
                coverImageURL = null,
                subscriptionId = null,
            ),
            subscription = null,
        )
    }

    private fun conversation(
        id: String,
        updatedAt: Long,
        subject: String? = "Conversation Subject",
        participantIDs: List<String>? = emptyList(),
        lastReplyAt: Long = updatedAt,
        lastIncomingReplyAt: Long? = null,
        lastReadAt: Long? = null,
        lastIncomingParticipantID: String? = null,
    ): ConversationEntity {
        return ConversationEntity(
            id = id,
            subject = subject,
            lastReplyAt = lastReplyAt,
            lastIncomingReplyAt = lastIncomingReplyAt,
            lastIncomingParticipantID = lastIncomingParticipantID,
            lastReadAt = lastReadAt,
            lastReadReplyID = null,
            lastReplyPreview = null,
            createdAt = 1704067200000L,
            participantIDs = participantIDs,
            updatedAt = updatedAt,
        )
    }

    private fun reply(
        id: String,
        conversationID: String,
        createdAt: Long,
        participantID: String?,
        text: String,
    ): ReplyEntity {
        return ReplyEntity(
            id = id,
            conversationID = conversationID,
            senderType = if (participantID != null) "participant" else "fan",
            participantID = participantID,
            externalID = null,
            createdAt = createdAt,
            content = listOf(
                ReplyContentBlock(
                    type = ReplyContentBlock.TYPE_TEXT,
                    text = text,
                    url = null,
                )
            ),
        )
    }

    private fun iso(epochMillis: Long): Long = epochMillis
}
