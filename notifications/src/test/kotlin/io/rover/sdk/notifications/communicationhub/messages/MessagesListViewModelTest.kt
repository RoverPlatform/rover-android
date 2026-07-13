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
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsHistorySync
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.posts.PostsDataSource
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription

import java.net.ConnectException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private class FakePostsDataSource : PostsDataSource {
        override fun getPostsFlow(): Flow<List<PostWithSubscription>> = flowOf(emptyList())

        override suspend fun markPostAsRead(postId: String) {
            Unit
        }
    }

    private class FakeConversationsDataSource : ConversationsDataSource {
        val resetVersionFlow = MutableStateFlow(0L)

        override suspend fun hasConversation(conversationId: String): Boolean = true

        override fun getConversationsFlow(): Flow<List<ConversationEntity>> = flowOf(emptyList())

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
