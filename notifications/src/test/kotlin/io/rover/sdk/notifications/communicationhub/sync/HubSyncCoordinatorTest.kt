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

package io.rover.sdk.notifications.communicationhub.sync

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import io.rover.sdk.notifications.communicationhub.AndroidDeliveredHubNotificationClearer
import io.rover.sdk.notifications.communicationhub.HubPushKind
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.conversations.RoomConversationsTransactionRunner
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.data.network.MarkConversationReadRequest
import io.rover.sdk.notifications.communicationhub.data.network.SendConversationReplyRequest
import io.rover.sdk.notifications.communicationhub.navigation.HubNavigation
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import retrofit2.Response

class HubSyncCoordinatorTest : RoverEngageTestBase() {

    private companion object {
        // Matches the integer id every Rover notification is posted with ("ROVR"); the tag is what
        // distinguishes individual notifications.
        const val ROVER_NOTIFICATION_ID = 0x524F5652
        const val TEST_CHANNEL_ID = "test-channel"
    }

    @Test
    fun resetAfter410ClearsEngageDataAndNavigatesHome() = runTest {
        val hubCoordinator = HubCoordinator()
        val coordinator = buildHubSyncCoordinator(hubCoordinator)
        var resetObserverCalled = false
        coordinator.registerResetObserver(object : HubResetObserver {
            override suspend fun onHubReset() {
                resetObserverCalled = true
            }
        })
        seedEngageData()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(include = "participants", cursor = null, before = null)

        val resetRequired = try {
            coordinator.getConversations(cursor = null, before = null)
            fail("Expected SyncResetRequiredException")
            return@runTest
        } catch (error: SyncResetRequiredException) {
            error
        }
        resetRequired.resetHandler.resetAfterSyncCancellation()

        assertThat(database!!.postsDao().getPostById("post-1"), equalTo(null as PostEntity?))
        assertThat(database!!.subscriptionsDao().getAllSubscriptions(), equalTo(emptyList()))
        assertThat(database!!.conversationsDao().getConversationById("conversation-1"), equalTo(null as ConversationEntity?))
        assertThat(database!!.participantsDao().getAllParticipants(), equalTo(emptyList()))
        assertThat(database!!.repliesDao().getRepliesForConversation("conversation-1"), equalTo(emptyList()))
        assertThat(database!!.syncStateDao().getSyncState("posts"), equalTo(null as SyncStateEntity?))
        assertThat(resetObserverCalled, equalTo(true))
        assertThat(hubCoordinator.pendingNavigation.value, equalTo(HubNavigation.Home))
    }

    /**
     * Regression guard: a 410 reset must clear delivered Hub notifications for both posts and
     * conversations, independent of Room contents, while leaving classic campaign notifications in
     * place. Before this fix Android cleared no delivered notifications on reset, leaving stale,
     * tappable Hub notifications in the tray after the underlying rows were dropped.
     */
    @Test
    fun resetAfter410ClearsDeliveredHubNotificationsButNotClassic() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ensureChannelExists(context)
        val notificationManager = NotificationManagerCompat.from(context)

        postNotification(context, tag = "post-notif", id = ROVER_NOTIFICATION_ID, kind = HubPushKind.POST)
        postNotification(context, tag = "conv-notif", id = ROVER_NOTIFICATION_ID, kind = HubPushKind.CONVERSATION)
        postNotification(context, tag = "classic-notif", id = ROVER_NOTIFICATION_ID, kind = null)

        assertThat(notificationManager.activeNotifications.size, equalTo(3))

        val coordinator = buildHubSyncCoordinator(HubCoordinator())
        seedEngageData()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(include = "participants", cursor = null, before = null)

        val resetRequired = try {
            coordinator.getConversations(cursor = null, before = null)
            fail("Expected SyncResetRequiredException")
            return@runTest
        } catch (error: SyncResetRequiredException) {
            error
        }
        resetRequired.resetHandler.resetAfterSyncCancellation()

        val remainingTags = notificationManager.activeNotifications.map { it.tag }
        assertThat(remainingTags, equalTo(listOf("classic-notif")))
    }

    @Test
    fun stale410FromOldGenerationDoesNotResetFreshData() = runTest {
        val hubCoordinator = HubCoordinator()
        val firstOldGenerationResponse = CompletableDeferred<Response<okhttp3.ResponseBody>>()
        val secondOldGenerationResponse = CompletableDeferred<Response<okhttp3.ResponseBody>>()
        val firstOldGenerationStarted = CompletableDeferred<Unit>()
        val secondOldGenerationStarted = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        val fakeEngageApiService = object : UnsupportedEngageApiService() {
            override suspend fun getConversations(
                include: String?,
                cursor: String?,
                before: String?,
            ): Response<okhttp3.ResponseBody> {
                return when (callCount.incrementAndGet()) {
                    1 -> {
                        firstOldGenerationStarted.complete(Unit)
                        firstOldGenerationResponse.await()
                    }
                    2 -> {
                        secondOldGenerationStarted.complete(Unit)
                        secondOldGenerationResponse.await()
                    }
                    3 -> Response.success("{}".toResponseBody(null))
                    else -> throw AssertionError("Unexpected getConversations call")
                }
            }
        }
        val coordinator = buildHubSyncCoordinator(hubCoordinator, fakeEngageApiService)
        var resetObserverCallCount = 0
        coordinator.registerResetObserver(object : HubResetObserver {
            override suspend fun onHubReset() {
                resetObserverCallCount += 1
            }
        })
        seedEngageData()

        val firstOldGenerationCall = async {
            try {
                coordinator.getConversations(cursor = null, before = null)
                throw AssertionError("Expected first old generation request to require reset")
            } catch (resetRequired: SyncResetRequiredException) {
                resetRequired
            }
        }
        val secondOldGenerationCall = async {
            coordinator.getConversations(cursor = null, before = null)
        }

        firstOldGenerationStarted.await()
        secondOldGenerationStarted.await()
        firstOldGenerationResponse.complete(Response.error(410, "gone".toResponseBody(null)))

        firstOldGenerationCall.await().resetHandler.resetAfterSyncCancellation()
        database!!.postsDao().upsertPost(
            PostEntity(
                id = "fresh-post",
                subject = "Fresh",
                previewText = "Fresh data",
                receivedAt = 2L,
                url = null,
                isRead = false,
                coverImageURL = null,
                subscriptionId = null,
            )
        )

        coordinator.getConversations(cursor = null, before = null)
        secondOldGenerationResponse.complete(Response.error(410, "gone".toResponseBody(null)))
        val staleResponse = secondOldGenerationCall.await()

        assertThat(staleResponse.response.code(), equalTo(410))
        assertThat(coordinator.currentGeneration(), equalTo(1L))
        assertThat(resetObserverCallCount, equalTo(1))
        assertThat(database!!.postsDao().getPostById("fresh-post")?.subject, equalTo("Fresh"))
        assertThat(hubCoordinator.pendingNavigation.value, equalTo(HubNavigation.Home))
    }

    private fun ensureChannelExists(context: Context) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                TEST_CHANNEL_ID,
                "Test",
                android.app.NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private fun postNotification(context: Context, tag: String, id: Int, kind: HubPushKind?) {
        val builder = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(tag)
        kind?.let { HubPushNotification.stamp(builder, it) }
        NotificationManagerCompat.from(context).notify(tag, id, builder.build())
    }

    private fun buildHubSyncCoordinator(
        hubCoordinator: HubCoordinator,
        engageApiService: EngageApiService = mockEngageApiService,
    ): HubSyncCoordinator {
        val database = database!!
        return HubSyncCoordinator(
            engageApiService = engageApiService,
            postsDao = database.postsDao(),
            subscriptionsDao = database.subscriptionsDao(),
            conversationsDao = database.conversationsDao(),
            repliesDao = database.repliesDao(),
            participantsDao = database.participantsDao(),
            syncStateDao = database.syncStateDao(),
            transactionRunner = RoomConversationsTransactionRunner(database),
            hubCoordinator = hubCoordinator,
            deliveredHubNotificationClearer = AndroidDeliveredHubNotificationClearer(
                ApplicationProvider.getApplicationContext()
            ),
        )
    }

    private open class UnsupportedEngageApiService : EngageApiService {
        override suspend fun getPosts(deviceIdentifier: String, cursor: String?): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getSubscriptions(): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getParticipants(): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun sendConversationReply(
            conversationId: String,
            body: SendConversationReplyRequest,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getConversations(
            include: String?,
            cursor: String?,
            before: String?,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun getConversationReplies(
            conversationId: String,
            cursor: String?,
            before: String?,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()

        override suspend fun markConversationRead(
            conversationId: String,
            body: MarkConversationReadRequest,
        ): Response<okhttp3.ResponseBody> =
            throw UnsupportedOperationException()
    }

    private suspend fun seedEngageData() {
        database!!.subscriptionsDao().upsertSubscription(
            SubscriptionEntity(
                id = "subscription-1",
                name = "Subscription",
                description = "Description",
                optIn = true,
                status = "published",
                logoURL = null,
            )
        )
        database!!.postsDao().upsertPost(
            PostEntity(
                id = "post-1",
                subject = "Post",
                previewText = "Preview",
                receivedAt = 1L,
                url = null,
                isRead = false,
                coverImageURL = null,
                subscriptionId = "subscription-1",
            )
        )
        database!!.participantsDao().upsertParticipant(
            ParticipantEntity(
                id = "participant-1",
                name = "Participant",
                bio = null,
                avatarUrl = null,
                updatedAt = 1L,
            )
        )
        database!!.conversationsDao().upsertConversation(
            ConversationEntity(
                id = "conversation-1",
                subject = "Conversation",
                lastReplyAt = 1L,
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = 1L,
                participantIDs = listOf("participant-1"),
                updatedAt = 1L,
            )
        )
        database!!.repliesDao().upsertReply(
            ReplyEntity(
                id = "reply-1",
                conversationID = "conversation-1",
                senderType = ReplyEntity.SENDER_TYPE_PARTICIPANT,
                participantID = "participant-1",
                externalID = null,
                createdAt = 1L,
                content = listOf(ReplyContentBlock(ReplyContentBlock.TYPE_TEXT, "Hello", null)),
            )
        )
        database!!.syncStateDao().replaceSyncState(
            SyncStateEntity(
                roverEntity = "posts",
                forwardCursor = "cursor",
                backwardCursor = null,
                historyComplete = false,
            )
        )
    }
}
