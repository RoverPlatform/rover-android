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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T0
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T1
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T2
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T3
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T4
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T5
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T6
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T7
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures.T8
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ConversationsDaoTest : RoverEngageTestBase() {

    @Test
    fun conversationsUpsertByIdIsIdempotent() = runBlocking {
        val dao = database!!.conversationsDao()
        val original = ConversationTestFixtures.conversation(
            id = "conversation-1",
            lastReplyAt = T3,
            lastIncomingReplyAt = T2,
            lastReadAt = T1,
            lastReadReplyID = "reply-1",
            lastReplyPreview = "Original preview",
            createdAt = T0,
            participantIDs = listOf("participant-1"),
            updatedAt = T4,
        )

        val updated = original.copy(
            subject = "Updated",
            lastReplyAt = T7,
            lastIncomingReplyAt = T6,
            lastReadAt = T5,
            lastReadReplyID = "reply-2",
            lastReplyPreview = "Updated preview",
            updatedAt = T8,
        )

        dao.upsertConversation(original)
        dao.upsertConversation(updated)

        assertThat(dao.getAllConversations().size, equalTo(1))
        val retrieved = dao.getConversationById("conversation-1")
        assertThat(retrieved?.subject, equalTo("Updated"))
        assertThat(retrieved?.lastReplyAt, equalTo(1704672000000L))
        assertThat(retrieved?.lastIncomingReplyAt, equalTo(1704585600000L))
        assertThat(retrieved?.lastReadAt, equalTo(1704499200000L))
        assertThat(retrieved?.lastReadReplyID, equalTo("reply-2"))
        assertThat(retrieved?.lastReplyPreview, equalTo("Updated preview"))
        assertThat(retrieved?.createdAt, equalTo(1704067200000L))
    }

    @Test
    fun conversationsPersistNullableMetadataAndAbsentParticipantIDs() = runBlocking {
        val dao = database!!.conversationsDao()
        val conversation = ConversationTestFixtures.conversation(
            id = "conversation-2",
            subject = "Missing participants",
            lastReplyAt = T3,
            updatedAt = T4,
        )

        dao.upsertConversation(conversation)

        val retrieved = dao.getConversationById("conversation-2")
        assertThat(retrieved?.participantIDs, equalTo(null as List<String>?))
        assertThat(retrieved?.lastIncomingReplyAt, equalTo(null as Long?))
        assertThat(retrieved?.lastReadAt, equalTo(null as Long?))
        assertThat(retrieved?.lastReadReplyID, equalTo(null as String?))
        assertThat(retrieved?.lastReplyPreview, equalTo(null as String?))
    }

    @Test
    fun conversationsOrderByUpdatedAtDescending() = runBlocking {
        val dao = database!!.conversationsDao()

        dao.upsertConversations(
            listOf(
                ConversationTestFixtures.conversation(id = "conversation-early", lastReplyAt = T0, createdAt = T0),
                ConversationTestFixtures.conversation(id = "conversation-late", lastReplyAt = 1706745600000L, createdAt = 1706745600000L), // 2024-02-01
            )
        )

        assertThat(dao.getAllConversations().map { it.id }, equalTo(listOf("conversation-late", "conversation-early")))
    }

    @Test
    fun unreadConversationCountIncludesConversationWithoutHydratedReplies() = runBlocking {
        val dao = database!!.conversationsDao()

        dao.upsertConversation(
            ConversationTestFixtures.conversation(
                id = "conversation-unread",
                lastReplyAt = T3,
                lastIncomingReplyAt = T2,
                lastReadAt = null,
            )
        )

        assertThat(dao.getUnreadConversationCountFlow().first(), equalTo(1))
    }

    @Test
    fun unreadConversationCountExcludesReadConversation() = runBlocking {
        val dao = database!!.conversationsDao()

        dao.upsertConversation(
            ConversationTestFixtures.conversation(
                id = "conversation-read",
                lastReplyAt = T3,
                lastIncomingReplyAt = T2,
                lastReadAt = T2,
            )
        )

        assertThat(dao.getUnreadConversationCountFlow().first(), equalTo(0))
    }

    @Test
    fun unreadConversationCountFallsBackToLastReplyAtWhenIncomingTimestampMissing() = runBlocking {
        val dao = database!!.conversationsDao()

        dao.upsertConversation(
            ConversationTestFixtures.conversation(
                id = "conversation-fallback",
                lastReplyAt = T3,
                lastIncomingReplyAt = null,
                lastReadAt = T2,
            )
        )

        assertThat(dao.getUnreadConversationCountFlow().first(), equalTo(1))
    }

}
