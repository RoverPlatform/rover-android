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
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.notifications.communicationhub.InMemoryLocalStorage
import io.rover.sdk.notifications.communicationhub.InboxSeenWatermark
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostsDataSource
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity

import java.net.ConnectException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.reactivestreams.Publisher

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesListViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onMessagesListRevealedTriggersConversationBackfill() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync()
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )

        advanceUntilIdle()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(conversationsSync.backfillTriggerCount, equalTo(1))
    }

    @Test
    fun onMessagesListRevealedSetsErrorWhenConversationBackfillFails() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync().apply {
            backfillException = ConnectException("connection refused")
        }
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error, equalTo("connection refused"))

        stateCollectionJob.cancel()
    }

    @Test
    fun onMessagesListRevealedDoesNotSetErrorWhenConversationBackfillIsCancelled() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync().apply {
            backfillException = CancellationException("cancelled")
        }
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error, equalTo(null))

        stateCollectionJob.cancel()
    }

    @Test
    fun refreshUsesCoordinator() = runTest(timeout = 10.seconds) {
        val syncCoordinator = FakeSyncCoordinator(
            queuedResults = ArrayDeque(
                listOf(
                    SyncCoordinatorInterface.Result.Succeeded,
                    SyncCoordinatorInterface.Result.Succeeded,
                )
            )
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = syncCoordinator,
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()
        val callsAfterInit = syncCoordinator.triggerSyncCallCount

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(syncCoordinator.triggerSyncCallCount, equalTo(callsAfterInit + 1))
        assertThat(viewModel.uiState.value.error, equalTo(null))
        assertThat(viewModel.uiState.value.isRefreshing, equalTo(false))

        stateCollectionJob.cancel()
    }

    @Test
    fun initialRefreshSetsErrorWhenCoordinatorNeedsRetry() = runTest(timeout = 10.seconds) {
        val syncCoordinator = FakeSyncCoordinator(
            fallbackResult = SyncCoordinatorInterface.Result.RetryNeeded,
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = syncCoordinator,
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error, equalTo("Failed to refresh messages"))
        assertThat(viewModel.uiState.value.isRefreshing, equalTo(false))

        stateCollectionJob.cancel()
    }

    @Test
    fun forwardPollingSyncsOnEachIntervalButNotImmediately() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync()
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
            pollingIntervalMs = 10_000L,
        )
        advanceUntilIdle()

        viewModel.startForwardPolling()
        runCurrent()
        // Reveal already performs a forward sync; the poll waits a full interval first.
        assertThat(conversationsSync.syncForwardCount, equalTo(0))

        advanceTimeBy(10_000)
        runCurrent()
        assertThat(conversationsSync.syncForwardCount, equalTo(1))

        advanceTimeBy(10_000)
        runCurrent()
        assertThat(conversationsSync.syncForwardCount, equalTo(2))

        viewModel.stopForwardPolling()
    }

    @Test
    fun stopForwardPollingStopsSyncing() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync()
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
            pollingIntervalMs = 10_000L,
        )
        advanceUntilIdle()

        viewModel.startForwardPolling()
        advanceTimeBy(10_000)
        runCurrent()
        assertThat(conversationsSync.syncForwardCount, equalTo(1))

        viewModel.stopForwardPolling()
        advanceTimeBy(30_000)
        runCurrent()
        assertThat(conversationsSync.syncForwardCount, equalTo(1))
    }

    @Test
    fun forwardPollingContinuesAfterFailureWithoutSettingError() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync().apply {
            syncForwardException = ConnectException("connection refused")
        }
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
            pollingIntervalMs = 10_000L,
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()

        viewModel.startForwardPolling()
        advanceTimeBy(10_000)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertThat(conversationsSync.syncForwardCount, equalTo(2))
        assertThat(viewModel.uiState.value.error, equalTo(null))

        viewModel.stopForwardPolling()
        stateCollectionJob.cancel()
    }

    @Test
    fun startForwardPollingIsIdempotent() = runTest(timeout = 10.seconds) {
        val conversationsSync = FakeConversationsHistorySync()
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
            pollingIntervalMs = 10_000L,
        )
        advanceUntilIdle()

        viewModel.startForwardPolling()
        viewModel.startForwardPolling()
        advanceTimeBy(10_000)
        runCurrent()
        assertThat(conversationsSync.syncForwardCount, equalTo(1))

        viewModel.stopForwardPolling()
    }

    @Test
    fun onMessagesListConcealedMarksSeenAgainWithoutConsultingTheDeviceClock() = runTest(timeout = 10.seconds) {
        var clock = Instant.parse("2026-08-10T12:00:00Z")
        val newestReceivedAt = Instant.parse("2026-08-10T12:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(post("newest", newestReceivedAt.toEpochMilli())),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.onMessagesListRevealed()
        viewModel.awaitFirstPostsEmission()

        // Leaving the list marks seen a second time, but the watermark only records observed item
        // timestamps: the device clock has moved on to 14:00 and must be ignored, otherwise a
        // post stamped between the newest stored item and "now" would be suppressed unseen.
        clock = Instant.parse("2026-08-10T14:00:00Z")
        viewModel.onMessagesListConcealed()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
    }

    @Test
    fun onMessagesListForegroundedAdvancesTheWatermarkWithoutTriggeringBackfill() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val newestReceivedAt = Instant.parse("2026-08-10T13:00:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val conversationsSync = FakeConversationsHistorySync()
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(post("newest", newestReceivedAt.toEpochMilli())),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = conversationsSync,
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()

        // The user backgrounded the app on the list and came back: a post that arrived in between
        // must stop badging, but the history backfill must not re-run on every foreground.
        viewModel.onMessagesListForegrounded()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
        assertThat(conversationsSync.backfillTriggerCount, equalTo(0))
    }

    @Test
    fun onMessagesListRevealedAdvancesTheWatermarkToTheNewestPostReceivedAt() = runTest(timeout = 10.seconds) {
        // The watermark takes the newest receivedAt in the store, not the device clock (which is
        // running behind the posts here) and not just any post in the emission.
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val newestReceivedAt = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(
                    post("older", Instant.parse("2026-08-10T13:00:00Z").toEpochMilli()),
                    post("newest", newestReceivedAt.toEpochMilli()),
                ),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
    }

    @Test
    fun onMessagesListConcealedAdvancesTheWatermarkToTheNewestPostReceivedAt() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val newestReceivedAt = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(post("newest", newestReceivedAt.toEpochMilli())),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()

        viewModel.onMessagesListConcealed()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(newestReceivedAt.toEpochMilli()))
    }

    @Test
    fun revealBeforeTheFirstPostsEmissionLeavesTheWatermarkAlone() = runTest(timeout = 10.seconds) {
        val seed = Instant.parse("2026-08-10T12:00:00Z")
        var clock = seed
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(post("newest", Instant.parse("2026-08-10T13:30:00Z").toEpochMilli())),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )

        // No feed emission has arrived yet, so there is no item timestamp to record — and the
        // device clock (which has run on to 13:00) must not stand in for one.
        clock = Instant.parse("2026-08-10T13:00:00Z")
        viewModel.onMessagesListRevealed()

        assertThat(watermark.seenAt.value, equalTo(seed.toEpochMilli()))
    }

    @Test
    fun aPostArrivingWhileTheListIsRevealedAdvancesTheWatermark() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val alreadyThere = Instant.parse("2026-08-10T12:30:00Z")
        val arrivedWhileOpen = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val postsRepository = FakePostsDataSource(
            posts = listOf(post("already-there", alreadyThere.toEpochMilli())),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = postsRepository,
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()
        viewModel.onMessagesListRevealed()
        advanceUntilIdle()
        assertThat(watermark.seenAt.value, equalTo(alreadyThere.toEpochMilli()))

        // A post syncs in underneath the open list. The user is looking straight at it, so it must
        // be absorbed as it arrives rather than badging over the list until they navigate away.
        postsRepository.emit(
            listOf(
                post("already-there", alreadyThere.toEpochMilli()),
                post("arrived", arrivedWhileOpen.toEpochMilli()),
            ),
        )
        viewModel.awaitPostsEmission(rowCount = 2)

        assertThat(watermark.seenAt.value, equalTo(arrivedWhileOpen.toEpochMilli()))
    }

    @Test
    fun aPostArrivingWhileTheListIsConcealedDoesNotAdvanceTheWatermark() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val alreadyThere = Instant.parse("2026-08-10T12:30:00Z")
        val arrivedAfterLeaving = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val postsRepository = FakePostsDataSource(
            posts = listOf(post("already-there", alreadyThere.toEpochMilli())),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = postsRepository,
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.onMessagesListRevealed()
        viewModel.awaitFirstPostsEmission()
        viewModel.onMessagesListConcealed()
        advanceUntilIdle()

        // The user has left the list; a post arriving now has not been seen and must badge.
        postsRepository.emit(
            listOf(
                post("already-there", alreadyThere.toEpochMilli()),
                post("arrived", arrivedAfterLeaving.toEpochMilli()),
            ),
        )
        viewModel.awaitPostsEmission(rowCount = 2)

        assertThat(watermark.seenAt.value, equalTo(alreadyThere.toEpochMilli()))
    }

    @Test
    fun onMessagesListRevealedAdvancesTheWatermarkToTheNewestConversationActivity() = runTest(timeout = 10.seconds) {
        // Conversations badge by the same watermark rule as posts, so the visit must absorb their
        // activity too: here the newest item in the store is a conversation reply, not a post.
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val postReceivedAt = Instant.parse("2026-08-10T13:00:00Z")
        val conversationReplyAt = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                posts = listOf(post("post", postReceivedAt.toEpochMilli())),
            ),
            conversationsRepository = FakeConversationsDataSource(
                conversations = listOf(
                    conversation("convo", lastReplyAt = conversationReplyAt.toEpochMilli()),
                ),
            ),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(conversationReplyAt.toEpochMilli()))
    }

    @Test
    fun theWatermarkAbsorbsAnIncomingReplyTimestampNewerThanLastReplyAt() = runTest(timeout = 10.seconds) {
        // The push path can leave the newest activity on lastIncomingReplyAt alone; the watermark
        // must absorb the same inbound-only timestamp the badge compares, or the conversation
        // could never be cleared by a visit.
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val lastReplyAt = Instant.parse("2026-08-10T12:30:00Z")
        val incomingReplyAt = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = FakeConversationsDataSource(
                conversations = listOf(
                    conversation(
                        "convo",
                        lastReplyAt = lastReplyAt.toEpochMilli(),
                        lastIncomingReplyAt = incomingReplyAt.toEpochMilli(),
                    ),
                ),
            ),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()

        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        assertThat(watermark.seenAt.value, equalTo(incomingReplyAt.toEpochMilli()))
    }

    @Test
    fun anOutboundReplyDoesNotAdvanceTheWatermark() = runTest(timeout = 10.seconds) {
        val watermarkInstant = Instant.parse("2026-08-10T12:30:00Z")
        val outboundReplyAt = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(
            InMemoryLocalStorage(),
            now = { watermarkInstant },
        )
        val conversationsRepository = FakeConversationsDataSource(
            conversations = listOf(
                conversation(
                    "convo",
                    lastReplyAt = watermarkInstant.toEpochMilli(),
                    lastIncomingReplyAt = watermarkInstant.toEpochMilli(),
                ),
            ),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = conversationsRepository,
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()
        viewModel.onMessagesListRevealed()
        advanceUntilIdle()

        // A confirmed fan reply advances the direction-agnostic lastReplyAt. It must not move the
        // watermark beyond the inbound activity used by the badge.
        conversationsRepository.emit(
            listOf(
                conversation(
                    "convo",
                    lastReplyAt = outboundReplyAt.toEpochMilli(),
                    lastIncomingReplyAt = watermarkInstant.toEpochMilli(),
                ),
            ),
        )
        viewModel.uiState.first { state ->
            (state.rows.singleOrNull() as? MessageFeedRow.Conversation)?.timestamp ==
                outboundReplyAt.toEpochMilli()
        }

        assertThat(watermark.seenAt.value, equalTo(watermarkInstant.toEpochMilli()))
    }

    @Test
    fun aConversationReplyArrivingWhileTheListIsRevealedAdvancesTheWatermark() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val alreadyThere = Instant.parse("2026-08-10T12:30:00Z")
        val arrivedWhileOpen = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val conversationsRepository = FakeConversationsDataSource(
            conversations = listOf(conversation("convo", lastReplyAt = alreadyThere.toEpochMilli())),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = conversationsRepository,
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.awaitFirstPostsEmission()
        viewModel.onMessagesListRevealed()
        advanceUntilIdle()
        assertThat(watermark.seenAt.value, equalTo(alreadyThere.toEpochMilli()))

        // A reply syncs in on the open list: the conversation row updates in front of the user, so
        // its new activity is absorbed on arrival rather than badging over the list.
        conversationsRepository.emit(
            listOf(
                conversation("convo", lastReplyAt = alreadyThere.toEpochMilli()),
                conversation("convo-2", lastReplyAt = arrivedWhileOpen.toEpochMilli()),
            ),
        )
        viewModel.awaitPostsEmission(rowCount = 2)

        assertThat(watermark.seenAt.value, equalTo(arrivedWhileOpen.toEpochMilli()))
    }

    @Test
    fun aConversationReplyArrivingWhileTheListIsConcealedDoesNotAdvanceTheWatermark() = runTest(timeout = 10.seconds) {
        val clock = Instant.parse("2026-08-10T12:00:00Z")
        val alreadyThere = Instant.parse("2026-08-10T12:30:00Z")
        val arrivedAfterLeaving = Instant.parse("2026-08-10T13:30:00Z")
        val watermark = InboxSeenWatermark(InMemoryLocalStorage(), now = { clock })
        val conversationsRepository = FakeConversationsDataSource(
            conversations = listOf(conversation("convo", lastReplyAt = alreadyThere.toEpochMilli())),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(),
            conversationsRepository = conversationsRepository,
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = watermark,
        )
        viewModel.onMessagesListRevealed()
        viewModel.awaitFirstPostsEmission()
        viewModel.onMessagesListConcealed()
        advanceUntilIdle()

        // The user has left the list; a conversation reply arriving now has not been seen and must
        // badge.
        conversationsRepository.emit(
            listOf(
                conversation("convo", lastReplyAt = alreadyThere.toEpochMilli()),
                conversation("convo-2", lastReplyAt = arrivedAfterLeaving.toEpochMilli()),
            ),
        )
        viewModel.awaitPostsEmission(rowCount = 2)

        assertThat(watermark.seenAt.value, equalTo(alreadyThere.toEpochMilli()))
    }

    @Test
    fun selectSearchTokenScopesRowsAndClearsQuery() = runTest(timeout = 10.seconds) {
        val newsPost = PostWithSubscription(
            post = PostEntity(
                id = "post-news",
                subject = "Briefing",
                previewText = "Markets",
                receivedAt = 2000L,
                url = null,
                isRead = false,
                coverImageURL = null,
                subscriptionId = "sub-news",
            ),
            subscription = SubscriptionEntity(
                id = "sub-news",
                name = "News Daily",
                description = "",
                optIn = true,
                status = "published",
            ),
        )
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(listOf(newsPost, post(id = "post-other", receivedAt = 1000L))),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }
        viewModel.awaitPostsEmission(2)

        viewModel.updateSearchQuery("news")
        viewModel.selectSearchToken(HubSearchToken.Subscription(id = "sub-news", name = "News Daily"))

        // The feed recomputes asynchronously per search-state change, so an
        // emission for the intermediate query-only state ("news", no token) can
        // land after the token is set — and it also has exactly one row, with
        // suggestions. Wait for the fully settled scoped state instead of the
        // bare row count; the runTest timeout still fails the test if it never
        // arrives.
        val state = viewModel.uiState.first {
            it.rows.size == 1 && it.suggestions.isEmpty() && it.searchQuery.isEmpty() && it.searchToken != null
        }
        assertThat((state.rows.single() as MessageFeedRow.Post).postWithSubscription.post.id, equalTo("post-news"))
        assertThat(state.searchQuery, equalTo(""))
        assertThat(
            state.searchToken,
            equalTo<HubSearchToken?>(HubSearchToken.Subscription(id = "sub-news", name = "News Daily")),
        )
        assertThat(state.suggestions.isEmpty(), equalTo(true))

        stateCollectionJob.cancel()
    }

    @Test
    fun clearSearchTokenRestoresUnscopedRows() = runTest(timeout = 10.seconds) {
        val viewModel = MessagesListViewModel(
            postsRepository = FakePostsDataSource(
                listOf(post(id = "post-1", receivedAt = 2000L), post(id = "post-2", receivedAt = 1000L)),
            ),
            conversationsRepository = FakeConversationsDataSource(),
            conversationsSync = FakeConversationsHistorySync(),
            syncCoordinator = FakeSyncCoordinator(),
            inboxSeenWatermark = InboxSeenWatermark(InMemoryLocalStorage()),
        )
        val stateCollectionJob = backgroundScope.launch {
            viewModel.uiState.collect { }
        }
        viewModel.awaitPostsEmission(2)

        // Neither post belongs to this subscription, so the token empties the list.
        viewModel.selectSearchToken(HubSearchToken.Subscription(id = "sub-missing", name = "Missing"))
        viewModel.uiState.first { it.rows.isEmpty() }

        viewModel.clearSearchToken()
        viewModel.awaitPostsEmission(2)

        assertThat(viewModel.uiState.value.searchToken, equalTo<HubSearchToken?>(null))

        stateCollectionJob.cancel()
    }

    /**
     * Suspends until the feed has produced its first non-empty emission.
     *
     * The feed pipeline hops through `Dispatchers.Default`, which the test scheduler doesn't
     * control, so `advanceUntilIdle()` alone isn't enough to guarantee the posts emission (and
     * therefore the watermark write) has landed.
     */
    private suspend fun MessagesListViewModel.awaitFirstPostsEmission() {
        uiState.first { it.rows.isNotEmpty() }
    }

    /**
     * Suspends until the feed has produced an emission carrying exactly [rowCount] rows.
     *
     * Same reasoning as [awaitFirstPostsEmission]; used to await a *subsequent* emission after
     * pushing new posts through the repository fake. The view model writes the watermark before it
     * publishes the rows, so observing the rows means the watermark write has already happened.
     */
    private suspend fun MessagesListViewModel.awaitPostsEmission(rowCount: Int) {
        uiState.first { it.rows.size == rowCount }
    }

    private fun conversation(
        id: String,
        lastReplyAt: Long,
        lastIncomingReplyAt: Long? = lastReplyAt,
    ): ConversationEntity = ConversationTestFixtures.conversation(
        id = id,
        lastReplyAt = lastReplyAt,
        lastIncomingReplyAt = lastIncomingReplyAt,
    )

    private fun post(id: String, receivedAt: Long): PostWithSubscription = PostWithSubscription(
        post = PostEntity(
            id = id,
            subject = "Post $id",
            previewText = "Preview $id",
            receivedAt = receivedAt,
            url = null,
            isRead = false,
            coverImageURL = null,
            subscriptionId = null,
        ),
        subscription = null,
    )

    private class FakePostsDataSource(
        posts: List<PostWithSubscription> = emptyList(),
    ) : PostsDataSource {
        private val postsFlow = MutableStateFlow(posts)

        /** Pushes a new posts emission, standing in for a sync writing to the store. */
        fun emit(posts: List<PostWithSubscription>) {
            postsFlow.value = posts
        }

        override fun getPostsFlow(): Flow<List<PostWithSubscription>> = postsFlow

        override suspend fun markPostAsRead(postId: String) {
            Unit
        }
    }

    private class FakeConversationsDataSource(
        conversations: List<ConversationEntity> = emptyList(),
    ) : ConversationsDataSource {
        val resetVersionFlow = MutableStateFlow(0L)
        private val conversationsFlow = MutableStateFlow(conversations)

        /** Pushes a new conversations emission, standing in for a sync writing to the store. */
        fun emit(conversations: List<ConversationEntity>) {
            conversationsFlow.value = conversations
        }

        override suspend fun hasConversation(conversationId: String): Boolean = true

        override fun getConversationsFlow(): Flow<List<ConversationEntity>> = conversationsFlow

        override fun getParticipantsFlow(): Flow<List<ParticipantEntity>> = flowOf(emptyList())

        override fun getRepliesFlow(): Flow<List<ReplyEntity>> = flowOf(emptyList())

        override fun getResetVersionFlow(): StateFlow<Long> = resetVersionFlow

        override fun getRepliesBackwardCursorFlow(conversationId: String): Flow<String?> = flowOf(null)

        override suspend fun bootstrapLatestReplies(conversationId: String) {
            Unit
        }

        override fun startRepliesForwardPolling(conversationId: String) {
            Unit
        }

        override fun stopRepliesForwardPolling(conversationId: String) {
            Unit
        }

        override suspend fun loadOlderReplies(
            conversationId: String,
            beforeReplyId: String?,
            beforeCursor: String?,
        ) {
            Unit
        }

        override suspend fun markConversationRead(conversationId: String, lastReadReplyId: String?) {
            Unit
        }

        override suspend fun sendReply(
            conversationId: String,
            message: String,
            externalId: String?,
        ): String {
            return externalId ?: "external-id"
        }
    }

    private class FakeConversationsHistorySync : ConversationsHistorySync {
        var backfillTriggerCount = 0
        var backfillException: Exception? = null
        var syncForwardCount = 0
        var syncForwardException: Exception? = null

        override suspend fun triggerEagerBackwardHistoryBackfill() {
            backfillTriggerCount++
            backfillException?.let { throw it }
        }

        override suspend fun syncForward() {
            syncForwardCount++
            syncForwardException?.let { throw it }
        }
    }

    private class FakeSyncCoordinator(
        private val queuedResults: ArrayDeque<SyncCoordinatorInterface.Result> = ArrayDeque(),
        private val fallbackResult: SyncCoordinatorInterface.Result = SyncCoordinatorInterface.Result.Succeeded,
    ) : SyncCoordinatorInterface {
        var triggerSyncCallCount = 0

        override fun registerParticipant(participant: io.rover.sdk.core.data.sync.SyncParticipant) = Unit

        override fun registerStandaloneParticipant(participant: io.rover.sdk.core.data.sync.SyncStandaloneParticipant) = Unit

        @Suppress("DEPRECATION")
        override fun sync(): Publisher<SyncCoordinatorInterface.Result> {
            return Publishers.just(queuedResults.removeFirstOrNull() ?: fallbackResult)
        }

        override val syncResults: Publisher<SyncCoordinatorInterface.Result>
            get() = Publishers.defer {
                Publishers.just(queuedResults.removeFirstOrNull() ?: fallbackResult)
            }

        override val updates: Publisher<Unit> = Publishers.empty()

        override suspend fun awaitSync(): SyncCoordinatorInterface.Result {
            triggerSyncCallCount++
            return queuedResults.removeFirstOrNull() ?: fallbackResult
        }

        override fun triggerSync() {
            triggerSyncCallCount++
        }

        override fun ensureBackgroundSyncScheduled() = Unit
    }
}
