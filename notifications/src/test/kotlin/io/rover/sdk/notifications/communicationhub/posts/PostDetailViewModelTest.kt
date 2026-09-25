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

package io.rover.sdk.notifications.communicationhub.posts

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.reactivestreams.Publisher

/**
 * Runs against the real (in-memory) Room-backed [PostsRepository] from [RoverEngageTestBase].
 *
 * The view model's DB work hops through real dispatchers (`Dispatchers.IO`, Room's executor), which
 * virtual-time test scheduling cannot await — see the note on [RoverEngageTestBase] — so these
 * tests use `runBlocking` with bounded polling instead of `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest : RoverEngageTestBase() {

    override fun setupTest() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardownMain() {
        Dispatchers.resetMain()
    }

    /**
     * Marking read on open is what removes a post from the Hub badge, which counts posts that are
     * both unread and delivered after the inbox seen watermark. A post opened from a push tap or
     * deep link never composes the messages list, so nothing else would clear it: this path is the
     * only thing that keeps the badge clearable for users who only read posts from notifications.
     */
    @Test
    fun openingAPostMarksItRead() = runBlocking {
        val receivedAt = Instant.parse("2026-08-10T13:30:00Z")
        database!!.postsDao().upsertPost(
            PostEntity(
                id = "p1",
                subject = "Subject",
                previewText = "Preview",
                receivedAt = receivedAt.toEpochMilli(),
                url = null,
                isRead = false,
                coverImageURL = null,
                subscriptionId = null,
            )
        )

        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = FakeSyncCoordinator(),
            postId = "p1",
        )

        // The DAO fast path: the post is already persisted, so no sync is needed to surface it.
        awaitUntil { viewModel.uiState.value.post != null }
        awaitUntil { repository.getPostById("p1")?.isRead == true }

        assertThat(viewModel.uiState.value.post?.id, equalTo("p1"))
        assertThat(repository.getPostById("p1")?.isRead, equalTo(true))
    }

    // The tests below pin the slow path that the Hub's post detail route relies on once it
    // renders through the id overload of PostDetail: a post that is not in Room yet must
    // trigger a sync and then either appear or produce an error the screen can show.

    @Test
    fun aPostMissingFromRoomIsFetchedBySyncAndThenShown() = runBlocking {
        val syncCoordinator = FakeSyncCoordinator(
            onAwaitSync = {
                // The sync is what brings the post into Room.
                database!!.postsDao().upsertPost(
                    PostEntity("p2", "Synced subject", "Preview", 2L, null, false, null, null)
                )
                SyncCoordinatorInterface.Result.Succeeded
            }
        )

        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = syncCoordinator,
            postId = "p2",
        )

        awaitUntil { viewModel.uiState.value.post != null }

        assertThat(syncCoordinator.awaitSyncCalls, equalTo(1))
        assertThat(viewModel.uiState.value.post?.subject, equalTo("Synced subject"))
        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
        assertThat(viewModel.uiState.value.error, equalTo(null as String?))

        // Marking read must wait for the post to exist. An UPDATE that ran before the sync
        // inserted the row would hit nothing, and the post would keep counting on the badge.
        awaitUntil { repository.getPostById("p2")?.isRead == true }
        assertThat(repository.getPostById("p2")?.isRead, equalTo(true))
    }

    @Test
    fun aPostStillMissingAfterASuccessfulSyncReportsNotFound() = runBlocking {
        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = FakeSyncCoordinator(),
            postId = "does-not-exist",
        )

        awaitUntil { viewModel.uiState.value.error != null }

        assertThat(viewModel.uiState.value.error, equalTo("Post not found"))
        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
        assertThat(viewModel.uiState.value.post, equalTo(null as PostEntity?))
    }

    @Test
    fun aFailedSyncForAMissingPostReportsAConnectionError() = runBlocking {
        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = FakeSyncCoordinator(onAwaitSync = { SyncCoordinatorInterface.Result.RetryNeeded }),
            postId = "does-not-exist",
        )

        awaitUntil { viewModel.uiState.value.error != null }

        assertThat(
            viewModel.uiState.value.error,
            equalTo("Unable to load post. Please check your connection and try again."),
        )
        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
    }

    @Test
    fun aPostFetchedByASyncThatOtherwiseFailedIsStillShown() = runBlocking {
        // A full sync reports RetryNeeded when ANY participant fails. The posts participant can
        // have brought this post in regardless, and the screen must judge by the row, not by the
        // aggregate result, or the user sees a connection error over a post that is right there.
        val syncCoordinator = FakeSyncCoordinator(
            onAwaitSync = {
                database!!.postsDao().upsertPost(
                    PostEntity("p3", "Fetched despite failure", "Preview", 3L, null, false, null, null)
                )
                SyncCoordinatorInterface.Result.RetryNeeded
            }
        )

        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = syncCoordinator,
            postId = "p3",
        )

        awaitUntil { viewModel.uiState.value.post != null }

        assertThat(viewModel.uiState.value.post?.subject, equalTo("Fetched despite failure"))
        assertThat(viewModel.uiState.value.isLoading, equalTo(false))
        assertThat(viewModel.uiState.value.error, equalTo(null as String?))
        awaitUntil { repository.getPostById("p3")?.isRead == true }
    }

    @Test
    fun aPostArrivingAfterTheNotFoundErrorReplacesTheError() = runBlocking {
        val viewModel = PostDetailViewModel(
            postsRepository = repository,
            syncCoordinator = FakeSyncCoordinator(),
            postId = "p4",
        )
        awaitUntil { viewModel.uiState.value.error == "Post not found" }

        // A later background sync, or a push carrying the post, lands it in Room while the
        // error dialog is still up. The detail observes Room, so it shows the post.
        database!!.postsDao().upsertPost(
            PostEntity("p4", "Late arrival", "Preview", 4L, null, false, null, null)
        )

        awaitUntil { viewModel.uiState.value.post != null }
        assertThat(viewModel.uiState.value.post?.subject, equalTo("Late arrival"))
        assertThat(viewModel.uiState.value.error, equalTo(null as String?))
        awaitUntil { repository.getPostById("p4")?.isRead == true }
    }

    private suspend fun awaitUntil(timeoutMillis: Long = 5_000, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within ${timeoutMillis}ms" }
            delay(10)
        }
    }

    /**
     * @param onAwaitSync what a full sync does and reports; the default succeeds without touching
     *   the store, which is the "still missing after sync" case.
     */
    private class FakeSyncCoordinator(
        private val onAwaitSync: suspend () -> SyncCoordinatorInterface.Result = {
            SyncCoordinatorInterface.Result.Succeeded
        },
    ) : SyncCoordinatorInterface {
        var awaitSyncCalls: Int = 0
            private set

        override fun registerParticipant(participant: io.rover.sdk.core.data.sync.SyncParticipant) = Unit

        override fun registerStandaloneParticipant(participant: io.rover.sdk.core.data.sync.SyncStandaloneParticipant) = Unit

        @Suppress("DEPRECATION")
        override fun sync(): Publisher<SyncCoordinatorInterface.Result> =
            Publishers.just(SyncCoordinatorInterface.Result.Succeeded)

        override val syncResults: Publisher<SyncCoordinatorInterface.Result> =
            Publishers.just(SyncCoordinatorInterface.Result.Succeeded)

        override val updates: Publisher<Unit> = Publishers.empty()

        override suspend fun awaitSync(): SyncCoordinatorInterface.Result {
            awaitSyncCalls += 1
            return onAwaitSync()
        }

        override fun triggerSync() = Unit

        override fun ensureBackgroundSyncScheduled() = Unit
    }
}
