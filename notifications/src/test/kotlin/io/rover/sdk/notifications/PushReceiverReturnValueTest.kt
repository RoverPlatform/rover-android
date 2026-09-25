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

import android.os.Bundle
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.events.PushTokenTransmissionChannel
import io.rover.sdk.core.platform.DateFormatting
import io.rover.sdk.notifications.communicationhub.conversations.ConversationPushHandler
import io.rover.sdk.notifications.communicationhub.posts.PostPushHandler
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The receiver reports whether Rover handled the push, so a host's FirebaseMessagingService can
 * fall through to its own handling without peeking at the payload itself.
 *
 * True means Rover took the payload on: a Hub post or conversation was decoded and accepted, or a
 * legacy notification reached the dispatcher. A payload that is not Rover's, or one that is
 * addressed to Rover but cannot be decoded, is false — the host is told nothing was done with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PushReceiverReturnValueTest {
    private val postPushHandler = mock<PostPushHandler>()
    private val conversationPushHandler = mock<ConversationPushHandler>()
    private val notificationDispatcher = mock<NotificationDispatcher>()
    private val influenceTrackerService = mock<InfluenceTrackerServiceInterface>()

    private val receiver = makeReceiver(postPushHandler, conversationPushHandler)

    private fun makeReceiver(
        postPushHandler: PostPushHandler?,
        conversationPushHandler: ConversationPushHandler?,
    ) = PushReceiver(
        pushTokenTransmissionChannel = mock<PushTokenTransmissionChannel>(),
        notificationDispatcher = notificationDispatcher,
        dateFormatting = DateFormatting(),
        influenceTrackerService = influenceTrackerService,
        postPushHandler = postPushHandler,
        conversationPushHandler = conversationPushHandler,
    )

    @Test
    fun aPushWithoutTheRoverKeyIsNotHandled() {
        assertThat(receiver.onMessageReceivedData(mapOf("someone-else" to "{}")), equalTo(false))
    }

    @Test
    fun aRoverPayloadThatIsNotJsonIsNotHandled() {
        assertThat(receiver.onMessageReceivedData(mapOf("rover" to "{ not json")), equalTo(false))
    }

    @Test
    fun aRoverPayloadCarryingNothingRecognizedIsNotHandled() {
        assertThat(receiver.onMessageReceivedData(mapOf("rover" to "{}")), equalTo(false))
    }

    @Test
    fun anAcceptedPostPushIsHandled() {
        whenever(postPushHandler.handleCommunicationHubPush(any())).thenReturn(true)

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to POST_PAYLOAD)), equalTo(true))
    }

    @Test
    fun aPostPushTheHandlerRejectsIsNotHandled() {
        whenever(postPushHandler.handleCommunicationHubPush(any())).thenReturn(false)

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to POST_PAYLOAD)), equalTo(false))
    }

    @Test
    fun anAcceptedConversationPushIsHandled() {
        whenever(conversationPushHandler.handleCommunicationHubPush(any())).thenReturn(true)

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to CONVERSATION_PAYLOAD)), equalTo(true))
    }

    @Test
    fun aConversationPushMissingItsPiecesIsNotHandled() {
        whenever(conversationPushHandler.handleCommunicationHubPush(any())).thenReturn(true)

        val payload = """{ "conversation": {"id":"conversation-1"}, "reply": {"id":"reply-1"} }"""

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to payload)), equalTo(false))
    }

    @Test
    fun aLegacyNotificationThatReachesTheDispatcherIsHandled() {
        assertThat(receiver.onMessageReceivedData(mapOf("rover" to LEGACY_NOTIFICATION)), equalTo(true))
    }

    @Test
    fun aMalformedLegacyNotificationIsNotHandled() {
        assertThat(
            receiver.onMessageReceivedData(mapOf("rover" to """{"notification": "not-an-object"}""")),
            equalTo(false)
        )
    }

    /**
     * A payload can carry both, and each is decoded independently: an accepted Hub push still
     * reports handled even though the legacy notification beside it was not dispatched.
     */
    @Test
    fun anAcceptedHubPushIsHandledDespiteAMalformedNotificationBesideIt() {
        whenever(postPushHandler.handleCommunicationHubPush(any())).thenReturn(true)

        val payload = """{ "post": {"id":"post-1"}, "notification": "not-an-object" }"""

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to payload)), equalTo(true))
    }

    @Test
    fun theBundleVariantReportsHandledForALegacyNotification() {
        assertThat(receiver.onMessageReceivedDataAsBundle(bundleOfRover(LEGACY_NOTIFICATION)), equalTo(true))
    }

    @Test
    fun theBundleVariantReportsUnhandledWithoutTheRoverKey() {
        assertThat(receiver.onMessageReceivedDataAsBundle(Bundle()), equalTo(false))
    }

    /**
     * The dispatcher is only reached by the legacy-notification branch, so the Hub cases above
     * would report handled even if that branch had quietly started dispatching too.
     */
    @Test
    fun aHubPushAloneDoesNotReachTheDispatcher() {
        whenever(postPushHandler.handleCommunicationHubPush(any())).thenReturn(true)

        receiver.onMessageReceivedData(mapOf("rover" to POST_PAYLOAD))

        verify(notificationDispatcher, never()).ingest(any(), anyOrNull())
    }

    /**
     * A Hub push the handler rejects can still sit beside a notification that dispatches, so both
     * directions of "either was handled" are pinned rather than only the Hub one.
     */
    @Test
    fun aRejectedHubPushIsStillHandledWhenTheNotificationBesideItDispatches() {
        whenever(postPushHandler.handleCommunicationHubPush(any())).thenReturn(false)

        assertThat(receiver.onMessageReceivedData(mapOf("rover" to POST_PAYLOAD_WITH_NOTIFICATION)), equalTo(true))
        // Both halves still run: without this the test would also pass if a combined payload
        // skipped Hub processing altogether and only dispatched the notification.
        verify(postPushHandler).handleCommunicationHubPush(any())
        verify(notificationDispatcher).ingest(any(), anyOrNull())
    }

    @Test
    fun aHubPushWithNoHandlerRegisteredIsNotHandled() {
        val receiverWithoutHandlers = makeReceiver(postPushHandler = null, conversationPushHandler = null)

        assertThat(receiverWithoutHandlers.onMessageReceivedData(mapOf("rover" to POST_PAYLOAD)), equalTo(false))
        assertThat(
            receiverWithoutHandlers.onMessageReceivedData(mapOf("rover" to CONVERSATION_PAYLOAD)),
            equalTo(false)
        )
    }

    /**
     * The influenced-open candidate is cleared for a push that is not ours, so Rover does not take
     * credit for an app open that somebody else's push caused. The Bundle entry point has never
     * done this, and giving it a return value did not give it the behaviour either.
     */
    @Test
    fun onlyTheMapEntryPointClearsTheInfluencedOpenCandidate() {
        receiver.onMessageReceivedData(mapOf("someone-else" to "{}"))

        verify(influenceTrackerService).nonRoverPushReceived()
    }

    @Test
    fun theBundleEntryPointDoesNotClearTheInfluencedOpenCandidate() {
        receiver.onMessageReceivedDataAsBundle(Bundle())

        verify(influenceTrackerService, never()).nonRoverPushReceived()
    }

    @Test
    fun aRoverPushDoesNotClearTheInfluencedOpenCandidate() {
        receiver.onMessageReceivedData(mapOf("rover" to LEGACY_NOTIFICATION))

        verify(influenceTrackerService, never()).nonRoverPushReceived()
    }

    private fun bundleOfRover(roverJson: String): Bundle = Bundle().apply { putString("rover", roverJson) }

    private companion object {
        const val POST_PAYLOAD = """{ "post": {"id":"post-1"} }"""

        const val CONVERSATION_PAYLOAD = """
            {
              "conversation": {"id":"conversation-1"},
              "reply": {"id":"reply-1"},
              "participant": {"id":"participant-1"}
            }
        """

        const val POST_PAYLOAD_WITH_NOTIFICATION = """
            {
              "post": {"id":"post-1"},
              "notification": {
                "id": "notification-1",
                "campaignID": "campaign-1",
                "title": "Title",
                "body": "Body",
                "isRead": false,
                "isDeleted": false,
                "isNotificationCenterEnabled": false,
                "deliveredAt": "2026-08-31T09:00:00.000Z",
                "tapBehavior": { "__typename": "OpenAppNotificationTapBehavior" }
              }
            }
        """

        const val LEGACY_NOTIFICATION = """
            {
              "notification": {
                "id": "notification-1",
                "campaignID": "campaign-1",
                "title": "Title",
                "body": "Body",
                "isRead": false,
                "isDeleted": false,
                "isNotificationCenterEnabled": false,
                "deliveredAt": "2026-08-31T09:00:00.000Z",
                "tapBehavior": { "__typename": "OpenAppNotificationTapBehavior" }
              }
            }
        """
    }
}
