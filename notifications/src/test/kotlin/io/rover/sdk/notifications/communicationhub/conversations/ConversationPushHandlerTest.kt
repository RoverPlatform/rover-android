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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConversationPushHandlerTest {

    @Test
    fun conversationPushParsesPayloadPersistsAndNotifiesWithParticipantNameAndPreview() = runTest {
        val pushRepository = mock<ConversationPushRepository>()
        val notificationPresenter = mock<ConversationPushNotificationPresenter>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = ConversationPushHandler(
            conversationPushRepository = pushRepository,
            notificationPresenter = notificationPresenter,
            coroutineScope = testScope,
        )

        handler.handleCommunicationHubPush(
            """
                {
                  "conversation": {
                    "id": "conversation-1",
                    "subject": "",
                    "participantIDs": ["participant-1"],
                    "updatedAt": "2024-01-01T00:00:02Z",
                    "lastReplyAt": "2024-01-01T00:00:02Z",
                    "createdAt": "2024-01-01T00:00:00Z",
                    "lastIncomingReplyAt": "2024-01-01T00:00:02Z",
                    "lastReplyPreview": "Last preview"
                  },
                  "reply": {
                    "id": "reply-1",
                    "conversationID": "conversation-1",
                    "participantID": "participant-1",
                    "senderType": "participant",
                    "createdAt": "2024-01-01T00:00:02Z",
                    "content": [{"type": "text", "text": "Reply body"}]
                  },
                  "participant": {
                    "id": "participant-1",
                    "avatarURL": null,
                    "name": "Casey Jones",
                    "updatedAt": ""
                  }
                }
            """.trimIndent()
        )

        testScope.advanceUntilIdle()

        verify(pushRepository).saveConversationPushPayload(any(), any(), any())
        verify(notificationPresenter).presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "Casey Jones",
            participantAvatarUrl = null,
            body = "Last preview",
        )
    }

    @Test
    fun malformedConversationPushIsSafelyIgnored() = runTest {
        val pushRepository = mock<ConversationPushRepository>()
        val notificationPresenter = mock<ConversationPushNotificationPresenter>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = ConversationPushHandler(
            conversationPushRepository = pushRepository,
            notificationPresenter = notificationPresenter,
            coroutineScope = testScope,
        )

        handler.handleCommunicationHubPush("{\"conversation\":{\"id\":\"only\"}}")
        testScope.advanceUntilIdle()

        verify(pushRepository, never()).saveConversationPushPayload(any(), any(), any())
        verify(notificationPresenter, never()).presentConversationNotification(any(), any(), any(), any())
    }

    @Test
    fun omitsTitleWhenParticipantNameMissingEvenIfFirstAndLastNameArePresent() = runTest {
        val pushRepository = mock<ConversationPushRepository>()
        val notificationPresenter = mock<ConversationPushNotificationPresenter>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = ConversationPushHandler(
            conversationPushRepository = pushRepository,
            notificationPresenter = notificationPresenter,
            coroutineScope = testScope,
        )

        handler.handleCommunicationHubPush(
            """
                {
                  "conversation": {
                    "id": "conversation-1",
                    "subject": "",
                    "participantIDs": [],
                    "updatedAt": "2024-01-01T00:00:02Z",
                    "lastReplyAt": "2024-01-01T00:00:02Z",
                    "createdAt": "2024-01-01T00:00:00Z",
                    "lastIncomingReplyAt": "2024-01-01T00:00:02Z",
                    "lastReplyPreview": "Body"
                  },
                  "reply": {
                    "id": "reply-1",
                    "conversationID": "conversation-1",
                    "participantID": "participant-1",
                    "senderType": "participant",
                    "createdAt": "2024-01-01T00:00:02Z",
                    "content": [{"type": "text", "text": "Reply body"}]
                  },
                  "participant": {
                    "id": "participant-1",
                    "avatarURL": null,
                    "updatedAt": ""
                  }
                }
            """.trimIndent()
        )

        testScope.advanceUntilIdle()

        verify(notificationPresenter).presentConversationNotification(
            conversationId = "conversation-1",
            participantName = null,
            participantAvatarUrl = null,
            body = "Body",
        )
    }

    @Test
    fun parsesRealWorldPushPayloadWithIso8601Timestamps() = runTest {
        val pushRepository = mock<ConversationPushRepository>()
        val notificationPresenter = mock<ConversationPushNotificationPresenter>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = ConversationPushHandler(
            conversationPushRepository = pushRepository,
            notificationPresenter = notificationPresenter,
            coroutineScope = testScope,
        )

        // This is the real-world push payload format as sent by Bobcat.
        // The updatedAt and createdAt fields are ISO-8601 strings, not epoch millis integers.
        // The reply uses "content" and "createdAt" fields (not "blocks"/"sentAt").
        handler.handleCommunicationHubPush(
            """
                {
                  "conversation": {
                    "id": "019cd3da-e9d2-74c0-bc94-1a1bbf298dcd",
                    "subject": "ahoy hoy",
                    "createdAt": "2026-03-09T18:27:45.235Z",
                    "updatedAt": "2026-03-12T16:07:56.302Z",
                    "lastReplyAt": "2026-03-12T16:07:56.302Z",
                    "participantIDs": ["85cfaf55-864b-40f8-accb-3f5ae077d3a8"],
                    "lastReplyPreview": "e2e-test-member-ping-1",
                    "lastIncomingReplyAt": "2026-03-12T16:07:56.302Z"
                  },
                  "reply": {
                    "id": "019ce2cd-fc8e-7d33-8e2c-1cc02d13c7d4",
                    "content": [{"text": "e2e-test-member-ping-1", "type": "text"}],
                    "createdAt": "2026-03-12T16:07:56.302Z",
                    "senderType": "participant",
                    "participantID": "85cfaf55-864b-40f8-accb-3f5ae077d3a8",
                    "conversationID": "019cd3da-e9d2-74c0-bc94-1a1bbf298dcd"
                  },
                  "participant": {
                    "id": "85cfaf55-864b-40f8-accb-3f5ae077d3a8",
                    "bio": null,
                    "name": "Andrew Clunis",
                    "avatarURL": null,
                    "updatedAt": "2026-03-12T16:07:56.302Z"
                  }
                }
            """.trimIndent()
        )

        testScope.advanceUntilIdle()

        verify(pushRepository).saveConversationPushPayload(any(), any(), any())
        verify(notificationPresenter).presentConversationNotification(
            conversationId = "019cd3da-e9d2-74c0-bc94-1a1bbf298dcd",
            participantName = "Andrew Clunis",
            participantAvatarUrl = null,
            body = "e2e-test-member-ping-1",
        )
    }

    @Test
    fun usesParticipantNameExactlyAsProvided() = runTest {
        val pushRepository = mock<ConversationPushRepository>()
        val notificationPresenter = mock<ConversationPushNotificationPresenter>()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val handler = ConversationPushHandler(
            conversationPushRepository = pushRepository,
            notificationPresenter = notificationPresenter,
            coroutineScope = testScope,
        )

        handler.handleCommunicationHubPush(
            """
                {
                  "conversation": {
                    "id": "conversation-1",
                    "subject": "",
                    "participantIDs": [],
                    "updatedAt": "2024-01-01T00:00:02Z",
                    "lastReplyAt": "2024-01-01T00:00:02Z",
                    "createdAt": "2024-01-01T00:00:00Z",
                    "lastIncomingReplyAt": "2024-01-01T00:00:02Z",
                    "lastReplyPreview": "Body"
                  },
                  "reply": {
                    "id": "reply-1",
                    "conversationID": "conversation-1",
                    "participantID": "participant-1",
                    "senderType": "participant",
                    "createdAt": "2024-01-01T00:00:02Z",
                    "content": [{"type": "text", "text": "Reply body"}]
                  },
                  "participant": {
                    "id": "participant-1",
                    "avatarURL": null,
                    "name": "  Casey Jones  ",
                    "updatedAt": ""
                  }
                }
            """.trimIndent()
        )

        testScope.advanceUntilIdle()

        verify(notificationPresenter).presentConversationNotification(
            conversationId = "conversation-1",
            participantName = "  Casey Jones  ",
            participantAvatarUrl = null,
            body = "Body",
        )
    }
}
