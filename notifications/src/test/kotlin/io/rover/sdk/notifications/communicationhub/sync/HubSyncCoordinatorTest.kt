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
import io.rover.sdk.notifications.communicationhub.InMemoryLocalStorage
import io.rover.sdk.notifications.communicationhub.InboxSeenWatermark
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsTransactionRunner
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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
     * The Bench's Reset Hub Data, which has no 410 behind it.
     *
     * [HubSyncCoordinator.resetAfterSyncCancellation] is gated on an invalidation only a
     * server-driven 410 can arm, so it is a no-op for a reset the operator asked for. This is the
     * guard that [HubSyncCoordinator.resetHubDataOnDemand] performs the whole reset regardless —
     * the point being that a Bench that keeps running afterwards clears the *live* database rather
     * than unlinking its files out from under the open Room connection.
     */
    @Test
    fun resetOnDemandClearsEngageDataWithoutA410() = runTest {
        val hubCoordinator = HubCoordinator()
        val coordinator = buildHubSyncCoordinator(hubCoordinator)
        var resetObserverCalled = false
        coordinator.registerResetObserver(object : HubResetObserver {
            override suspend fun onHubReset() {
                resetObserverCalled = true
            }
        })
        seedEngageData()

        coordinator.resetHubDataOnDemand()

        assertThat(database!!.postsDao().getPostById("post-1"), equalTo(null as PostEntity?))
        assertThat(database!!.subscriptionsDao().getAllSubscriptions(), equalTo(emptyList()))
        assertThat(database!!.conversationsDao().getConversationById("conversation-1"), equalTo(null as ConversationEntity?))
        assertThat(database!!.participantsDao().getAllParticipants(), equalTo(emptyList()))
        assertThat(database!!.repliesDao().getRepliesForConversation("conversation-1"), equalTo(emptyList()))
        // The cursors go with the rows: without this the Hub would resume mid-stream on its next
        // sync instead of refetching, which is what the button's copy promises.
        assertThat(database!!.syncStateDao().getSyncState("posts"), equalTo(null as SyncStateEntity?))
        assertThat(resetObserverCalled, equalTo(true))
        assertThat(hubCoordinator.pendingNavigation.value, equalTo(HubNavigation.Home))
    }

    /**
     * The half of the reset that a bare `clearAllTables()` would not get: in-flight sync is
     * cancelled and joined first, and the generation advances so a response that was already on
     * its way cannot repopulate what was just cleared.
     */
    @Test
    fun resetOnDemandCancelsInFlightWorkAndBlocksStaleWrites() = runTest {
        val coordinator = buildHubSyncCoordinator(HubCoordinator())
        var cancelledInFlightWork = false
        coordinator.registerResetCancellable(object : HubResetCancellable {
            override suspend fun cancelAndJoinHubResetInvalidatedWork() {
                cancelledInFlightWork = true
            }
        })
        seedEngageData()
        val generationBeforeReset = coordinator.currentGeneration()

        coordinator.resetHubDataOnDemand()

        assertThat(cancelledInFlightWork, equalTo(true))
        assertThat(coordinator.currentGeneration(), equalTo(generationBeforeReset + 1))
        try {
            coordinator.ensureCanPersist(generationBeforeReset)
            fail("Expected a response captured before the reset to be refused")
        } catch (expected: StaleHubSyncGenerationException) {
            // The stale write was refused, which is the point.
        }
    }

    /**
     * The window on the far side of the cancellation hooks: a sync that starts *after* its own hook
     * has run, reads a cursor the delete transaction has not reached yet, and persists once it has.
     *
     * Cancelling the running syncs is not enough on its own to close that. The generation such a
     * sync captures is the one the reset itself advanced to, so it matches at persist time and the
     * write is accepted — the pre-reset cursor goes back into a table the reset had just emptied,
     * along with whatever page the server returned for it, and the next sync resumes mid-stream
     * instead of refetching. The gate holds the sync at the door until the reset is done, so it
     * reads the cleared state rather than the state on its way out.
     */
    @Test
    fun syncStartingDuringResetOnDemandCannotPersistBehindIt() = runTest {
        val deleteTransactionReached = CompletableDeferred<Unit>()
        val syncReadItsCursor = CompletableDeferred<Unit>()
        val coordinator = buildHubSyncCoordinator(HubCoordinator()) { roomTransactionRunner ->
            object : ConversationsTransactionRunner {
                override suspend fun <T> withTransaction(block: suspend () -> T): T {
                    deleteTransactionReached.complete(Unit)
                    // Hold the delete open long enough for a sync to race it. Virtual time, so a
                    // sync that is held at the gate instead costs the test nothing.
                    withTimeoutOrNull(10.seconds) { syncReadItsCursor.await() }
                    return roomTransactionRunner.withTransaction(block)
                }
            }
        }
        val syncJobs = HubResetJobTracker(
            cancellationMessage = "Test sync cancelled because Hub data was reset.",
            syncStartGate = coordinator,
        )
        coordinator.registerResetCancellable(syncJobs)
        seedEngageData()

        val reset = async { coordinator.resetHubDataOnDemand() }
        // The reset has advanced the generation and run the cancellation hooks by now: the tracker
        // holds no jobs, so a sync starting here is one the hooks can no longer reach.
        deleteTransactionReached.await()

        val sync = async {
            syncJobs.track {
                val cursor = database!!.syncStateDao().getSyncState("posts")?.forwardCursor
                val generation = coordinator.currentGeneration()
                syncReadItsCursor.complete(Unit)
                val persistAccepted = try {
                    coordinator.ensureCanPersist(generation)
                    true
                } catch (_: StaleHubSyncGenerationException) {
                    false
                }
                cursor to persistAccepted
            }
        }

        reset.await()
        val (cursorSeenBySync, persistAccepted) = sync.await()

        assertThat(
            "a sync that read a pre-reset cursor must not be allowed to write behind the reset",
            cursorSeenBySync != null && persistAccepted,
            equalTo(false),
        )
        // And it is held rather than refused: once the reset is done the sync starts cleanly, from
        // no cursor at all, which is the refetch the button promises.
        assertThat(cursorSeenBySync, equalTo(null as String?))
        assertThat(database!!.postsDao().getPostById("post-1"), equalTo(null as PostEntity?))
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

    /**
     * The refetched post history after a 410 arrives with its original `receivedAt` timestamps, so
     * a watermark from the previous identity must not suppress the new identity's content.
     */
    @Test
    fun resetAfter410ResetsTheInboxSeenWatermark() = runTest {
        var clock = java.time.Instant.parse("2026-08-10T12:00:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val coordinator = buildHubSyncCoordinator(HubCoordinator(), inboxSeenWatermark = watermark)
        seedEngageData()

        doReturn(Response.error<okhttp3.ResponseBody>(410, "gone".toResponseBody(null)))
            .whenever(mockEngageApiService)
            .getConversations(include = "participants", cursor = null, before = null)

        clock = java.time.Instant.parse("2026-08-10T13:00:00Z")
        val resetRequired = try {
            coordinator.getConversations(cursor = null, before = null)
            fail("Expected SyncResetRequiredException")
            return@runTest
        } catch (error: SyncResetRequiredException) {
            error
        }
        resetRequired.resetHandler.resetAfterSyncCancellation()

        assertThat(watermark.seenAt.value, equalTo(clock.toEpochMilli()))
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
        inboxSeenWatermark: InboxSeenWatermark? = null,
        wrapTransactionRunner: (ConversationsTransactionRunner) -> ConversationsTransactionRunner = { it },
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
            transactionRunner = wrapTransactionRunner(RoomConversationsTransactionRunner(database)),
            hubCoordinator = hubCoordinator,
            deliveredHubNotificationClearer = AndroidDeliveredHubNotificationClearer(
                ApplicationProvider.getApplicationContext()
            ),
            inboxSeenWatermark = inboxSeenWatermark,
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
