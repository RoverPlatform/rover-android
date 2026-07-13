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

package io.rover.sdk.notifications

import io.rover.sdk.core.events.PushTokenTransmissionChannel
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.notifications.communicationhub.conversations.ConversationPushHandler
import io.rover.sdk.notifications.communicationhub.posts.PostPushHandler
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushReceiverConversationPushRoutingTest {

    @Test
    fun routesConversationPayloadToConversationHandlerWhenAllPiecesPresent() {
        val conversationPushHandler = mock<ConversationPushHandler>()
        val receiver = PushReceiver(
            pushTokenTransmissionChannel = mock<PushTokenTransmissionChannel>(),
            notificationDispatcher = mock<NotificationDispatcher>(),
            dateFormatting = mock<DateFormattingInterface>(),
            influenceTrackerService = mock<InfluenceTrackerServiceInterface>(),
            postPushHandler = mock<PostPushHandler>(),
            conversationPushHandler = conversationPushHandler,
        )

        receiver.onMessageReceivedData(
            mapOf(
                "rover" to """
                    {
                      "conversation": {"id":"conversation-1"},
                      "reply": {"id":"reply-1"},
                      "participant": {"id":"participant-1"}
                    }
                """.trimIndent()
            )
        )

        verify(conversationPushHandler).handleCommunicationHubPush(any())
    }

    @Test
    fun doesNotRouteWhenConversationPayloadPiecesAreMissing() {
        val conversationPushHandler = mock<ConversationPushHandler>()
        val receiver = PushReceiver(
            pushTokenTransmissionChannel = mock<PushTokenTransmissionChannel>(),
            notificationDispatcher = mock<NotificationDispatcher>(),
            dateFormatting = mock<DateFormattingInterface>(),
            influenceTrackerService = mock<InfluenceTrackerServiceInterface>(),
            postPushHandler = mock<PostPushHandler>(),
            conversationPushHandler = conversationPushHandler,
        )

        receiver.onMessageReceivedData(
            mapOf(
                "rover" to """
                    {
                      "conversation": {"id":"conversation-1"},
                      "reply": {"id":"reply-1"}
                    }
                """.trimIndent()
            )
        )

        verify(conversationPushHandler, never()).handleCommunicationHubPush(any())
    }
}
