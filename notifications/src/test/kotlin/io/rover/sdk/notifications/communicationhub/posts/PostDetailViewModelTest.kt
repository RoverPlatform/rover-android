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

    private suspend fun awaitUntil(timeoutMillis: Long = 5_000, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within ${timeoutMillis}ms" }
            delay(10)
        }
    }

    private class FakeSyncCoordinator : SyncCoordinatorInterface {
        override fun registerParticipant(participant: io.rover.sdk.core.data.sync.SyncParticipant) = Unit

        override fun registerStandaloneParticipant(participant: io.rover.sdk.core.data.sync.SyncStandaloneParticipant) = Unit

        @Suppress("DEPRECATION")
        override fun sync(): Publisher<SyncCoordinatorInterface.Result> =
            Publishers.just(SyncCoordinatorInterface.Result.Succeeded)

        override val syncResults: Publisher<SyncCoordinatorInterface.Result> =
            Publishers.just(SyncCoordinatorInterface.Result.Succeeded)

        override val updates: Publisher<Unit> = Publishers.empty()

        override suspend fun awaitSync(): SyncCoordinatorInterface.Result =
            SyncCoordinatorInterface.Result.Succeeded

        override fun triggerSync() = Unit

        override fun ensureBackgroundSyncScheduled() = Unit
    }
}
