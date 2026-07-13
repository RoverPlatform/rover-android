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
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RepliesDaoTest : RoverEngageTestBase() {

    @Test
    fun repliesUpsertByIdIsIdempotentAndPreservesTypedBlocks() = runBlocking {
        val conversationsDao = database!!.conversationsDao()
        val repliesDao = database!!.repliesDao()

        conversationsDao.upsertConversation(
            ConversationTestFixtures.conversation(
                id = "conversation-1",
                subject = "Subject",
                lastReplyAt = T3,
                lastIncomingReplyAt = T2,
                lastReadAt = T1,
                lastReadReplyID = "reply-0",
                lastReplyPreview = "Preview",
                createdAt = T0,
                participantIDs = emptyList(),
                updatedAt = T4,
            )
        )

        val original = ConversationTestFixtures.reply(
            id = "reply-1",
            conversationId = "conversation-1",
            participantId = null,
            senderType = "participant", // participantID null but senderType "participant" — models a deleted participant
            externalID = "external-1",
            createdAt = T0,
            content = listOf(
                ConversationTestFixtures.textBlock("hello"),
                ConversationTestFixtures.imageBlock("https://example.com/image.png"),
            ),
        )
        val updated = original.copy(createdAt = T1, externalID = "external-2")

        repliesDao.upsertReply(original)
        repliesDao.upsertReply(updated)

        val replies = repliesDao.getRepliesForConversation("conversation-1")
        assertThat(replies.size, equalTo(1))
        assertThat(replies.first().createdAt, equalTo(1704153600000L))
        assertThat(replies.first().senderType, equalTo("participant"))
        assertThat(replies.first().externalID, equalTo("external-2"))
        assertThat(replies.first().content, equalTo(original.content))
    }
}
