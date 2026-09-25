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
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity


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
    fun subscriptionTokenNarrowsToThatSubscriptionsPostsById() {
        val newsDaily = subscription(id = "sub-a", name = "News")
        val duplicateName = subscription(id = "sub-b", name = "News")
        val match = post(id = "post-match", receivedAt = 4000L, subject = "Briefing", subscription = newsDaily)
        val otherSubscription = post(id = "post-other", receivedAt = 3000L, subject = "Briefing", subscription = duplicateName)
        val noSubscription = post(id = "post-none", receivedAt = 2000L, subject = "Briefing")
        val newsConversation = conversation(id = "conversation-news", updatedAt = 1000L, subject = "News chat")

        val rows = MessagesFeedBuilder.buildRows(
            posts = listOf(match, otherSubscription, noSubscription),
            conversations = listOf(newsConversation),
            participants = emptyList(),
            replies = emptyList(),
            searchQuery = "",
            searchToken = HubSearchToken.Subscription(id = "sub-a", name = "News"),
        )

        assertThat(rows.size, equalTo(1))
        assertThat((rows.single() as MessageFeedRow.Post).postWithSubscription.post.id, equalTo("post-match"))
    }

    @Test
    fun subscriptionTokenWithTextSearchesContentFieldsOnly() {
        val newsDaily = subscription(id = "sub-a", name = "News Daily")
        val scubaPost = post(id = "post-scuba", receivedAt = 2000L, subject = "Scuba trim counterweights", subscription = newsDaily)
        val otherPost = post(id = "post-recap", receivedAt = 1000L, subject = "Basketball recap", subscription = newsDaily)
        val token = HubSearchToken.Subscription(id = "sub-a", name = "News Daily")

        val scubaRows = MessagesFeedBuilder.buildRows(
            posts = listOf(scubaPost, otherPost),
            conversations = emptyList(),
            participants = emptyList(),
            replies = emptyList(),
            searchQuery = "scuba",
            searchToken = token,
        )
        assertThat(scubaRows.size, equalTo(1))
        assertThat((scubaRows.single() as MessageFeedRow.Post).postWithSubscription.post.id, equalTo("post-scuba"))

        // The subscription's own name must NOT match within a token —
        // only subject/preview do.
        val nameRows = MessagesFeedBuilder.buildRows(
            posts = listOf(scubaPost, otherPost),
            conversations = emptyList(),
            participants = emptyList(),
            replies = emptyList(),
            searchQuery = "daily",
            searchToken = token,
        )
        assertThat(nameRows.isEmpty(), equalTo(true))
    }

    @Test
    fun senderTokenNarrowsToConversationsWithThatParticipant() {
        val alice = participant(id = "p-alice", name = "Alice Johnson")
        val bob = participant(id = "p-bob", name = "Bob Newsome")
        val aliceConversation = conversation(
            id = "conversation-alice",
            updatedAt = 4000L,
            subject = "Refund",
            participantIDs = listOf("p-alice"),
        )
        val bothConversation = conversation(
            id = "conversation-both",
            updatedAt = 3000L,
            subject = "Group chat",
            participantIDs = listOf("p-alice", "p-bob"),
        )
        val bobConversation = conversation(
            id = "conversation-bob",
            updatedAt = 2000L,
            subject = "Alice in Wonderland book club",
            participantIDs = listOf("p-bob"),
        )
        val alicePost = post(id = "post-alice", receivedAt = 1000L, subject = "Alice Johnson featured")

        val rows = MessagesFeedBuilder.buildRows(
            posts = listOf(alicePost),
            conversations = listOf(aliceConversation, bothConversation, bobConversation),
            participants = listOf(alice, bob),
            replies = emptyList(),
            searchQuery = "",
            searchToken = HubSearchToken.Sender(participantID = "p-alice", name = "Alice Johnson"),
        )

        assertThat(
            rows.map { (it as MessageFeedRow.Conversation).id },
            equalTo(listOf("conversation-alice", "conversation-both")),
        )
    }

    @Test
    fun senderTokenWithTextSearchesContentFieldsOnly() {
        val alice = participant(id = "p-alice", name = "Alice Johnson")
        val refund = conversation(
            id = "conversation-refund",
            updatedAt = 2000L,
            subject = "Refund request",
            participantIDs = listOf("p-alice"),
            lastIncomingParticipantID = "p-alice",
        )
        val order = conversation(
            id = "conversation-order",
            updatedAt = 1000L,
            subject = "Order",
            participantIDs = listOf("p-alice"),
            lastIncomingParticipantID = "p-alice",
        )
        val token = HubSearchToken.Sender(participantID = "p-alice", name = "Alice Johnson")

        val refundRows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(refund, order),
            participants = listOf(alice),
            replies = emptyList(),
            searchQuery = "refund",
            searchToken = token,
        )
        assertThat(refundRows.size, equalTo(1))
        assertThat((refundRows.single() as MessageFeedRow.Conversation).id, equalTo("conversation-refund"))

        // The sender's own name must NOT match within a token.
        val nameRows = MessagesFeedBuilder.buildRows(
            posts = emptyList(),
            conversations = listOf(refund, order),
            participants = listOf(alice),
            replies = emptyList(),
            searchQuery = "johnson",
            searchToken = token,
        )
        assertThat(nameRows.isEmpty(), equalTo(true))
    }

    @Test
    fun suggestionsAreEmptyForBlankQuery() {
        val newsDaily = subscription(id = "sub-a", name = "News")
        val suggestions = MessagesFeedBuilder.buildSuggestions(
            posts = listOf(post(id = "post-1", receivedAt = 1000L, subscription = newsDaily)),
            conversations = emptyList(),
            participants = emptyList(),
            searchQuery = "  ",
        )
        assertThat(suggestions.isEmpty(), equalTo(true))
    }

    @Test
    fun suggestionsDedupeByIdAndSkipBlankNamesAndIds() {
        val newsDaily = subscription(id = "sub-daily", name = "News Daily")
        val placeholder = subscription(id = "sub-placeholder", name = "")
        val blankId = subscription(id = "", name = "News Void")

        val suggestions = MessagesFeedBuilder.buildSuggestions(
            posts = listOf(
                post(id = "post-1", receivedAt = 6000L, subscription = newsDaily),
                post(id = "post-2", receivedAt = 5000L, subscription = newsDaily),
                post(id = "post-3", receivedAt = 4000L, subscription = placeholder),
                post(id = "post-4", receivedAt = 3000L, subscription = blankId),
                post(id = "post-5", receivedAt = 2000L),
            ),
            conversations = emptyList(),
            participants = emptyList(),
            searchQuery = "news",
        )

        assertThat(
            suggestions,
            equalTo(listOf<HubSearchToken>(HubSearchToken.Subscription(id = "sub-daily", name = "News Daily"))),
        )
    }

    @Test
    fun suggestionsIncludeParticipantsAndAreCaseInsensitive() {
        val bob = participant(id = "p-bob", name = "Bob Newsome")
        val anonymous = participant(id = "p-anon", name = null)
        val chat = conversation(
            id = "conversation-chat",
            updatedAt = 1000L,
            subject = "Chat",
            participantIDs = listOf("p-bob", "p-anon"),
        )

        val suggestions = MessagesFeedBuilder.buildSuggestions(
            posts = emptyList(),
            conversations = listOf(chat),
            participants = listOf(bob, anonymous),
            searchQuery = "NEWS",
        )

        assertThat(
            suggestions,
            equalTo(listOf<HubSearchToken>(HubSearchToken.Sender(participantID = "p-bob", name = "Bob Newsome"))),
        )
    }

    @Test
    fun suggestionsCarryTheSubscriptionLogoAndParticipantAvatar() {
        val newsDaily = subscription(id = "sub-daily", name = "News Daily")
            .copy(logoURL = "https://example.com/logo.png")
        val plain = subscription(id = "sub-plain", name = "News Weekly")
        val bob = participant(id = "p-bob", name = "Bob Newsome")
            .copy(avatarUrl = " https://example.com/bob.png ")
        val anonymousAvatar = participant(id = "p-blank", name = "Newsome Blank")
            .copy(avatarUrl = "  ")

        val suggestions = MessagesFeedBuilder.buildSuggestions(
            posts = listOf(
                post(id = "post-1", receivedAt = 2000L, subscription = newsDaily),
                post(id = "post-2", receivedAt = 1000L, subscription = plain),
            ),
            conversations = listOf(
                conversation(id = "conversation-1", updatedAt = 1000L, participantIDs = listOf("p-bob", "p-blank")),
            ),
            participants = listOf(bob, anonymousAvatar),
            searchQuery = "news",
        )

        assertThat(
            suggestions,
            equalTo(
                listOf(
                    HubSearchToken.Subscription(id = "sub-daily", name = "News Daily", imageUrl = "https://example.com/logo.png"),
                    HubSearchToken.Subscription(id = "sub-plain", name = "News Weekly", imageUrl = null),
                    HubSearchToken.Sender(participantID = "p-bob", name = "Bob Newsome", imageUrl = "https://example.com/bob.png"),
                    HubSearchToken.Sender(participantID = "p-blank", name = "Newsome Blank", imageUrl = null),
                ),
            ),
        )
    }

    @Test
    fun suggestionsOrderSubscriptionsFirstThenLocaleAwareWithIdTieBreak() {
        val zebra = subscription(id = "sub-z", name = "Zebra News")
        val apple = subscription(id = "sub-a", name = "apple news")
        val tieB = subscription(id = "sub-tie-b", name = "News")
        val tieA = subscription(id = "sub-tie-a", name = "News")
        val bob = participant(id = "p-bob", name = "Bob Newsome")

        val suggestions = MessagesFeedBuilder.buildSuggestions(
            posts = listOf(
                post(id = "post-1", receivedAt = 4000L, subscription = zebra),
                post(id = "post-2", receivedAt = 3000L, subscription = apple),
                post(id = "post-3", receivedAt = 2000L, subscription = tieB),
                post(id = "post-4", receivedAt = 1000L, subscription = tieA),
            ),
            conversations = listOf(
                conversation(id = "conversation-chat", updatedAt = 1000L, subject = "Chat", participantIDs = listOf("p-bob")),
            ),
            participants = listOf(bob),
            searchQuery = "news",
        )

        assertThat(
            suggestions,
            equalTo(
                listOf(
                    HubSearchToken.Subscription(id = "sub-a", name = "apple news"),
                    HubSearchToken.Subscription(id = "sub-tie-a", name = "News"),
                    HubSearchToken.Subscription(id = "sub-tie-b", name = "News"),
                    HubSearchToken.Subscription(id = "sub-z", name = "Zebra News"),
                    HubSearchToken.Sender(participantID = "p-bob", name = "Bob Newsome"),
                ),
            ),
        )
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

    private fun post(
        id: String,
        receivedAt: Long,
        subject: String = "Post $id",
        previewText: String = "Preview $id",
        subscription: SubscriptionEntity? = null,
    ): PostWithSubscription {
        return PostWithSubscription(
            post = PostEntity(
                id = id,
                subject = subject,
                previewText = previewText,
                receivedAt = receivedAt,
                url = "https://example.com/$id",
                isRead = false,
                coverImageURL = null,
                subscriptionId = subscription?.id,
            ),
            subscription = subscription,
        )
    }

    private fun subscription(id: String, name: String): SubscriptionEntity {
        return SubscriptionEntity(
            id = id,
            name = name,
            description = "",
            optIn = true,
            status = "published",
        )
    }

    private fun participant(id: String, name: String?): ParticipantEntity {
        return ParticipantEntity(
            id = id,
            name = name,
            avatarUrl = null,
            updatedAt = 1704067200000L,
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
