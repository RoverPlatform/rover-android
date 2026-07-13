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

package io.rover.sdk.notifications.communicationhub.badge

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsRepository
import io.rover.sdk.notifications.communicationhub.posts.PostsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoverBadgeTest {

    @Test
    fun recomputesUnreadBadgeWhenPushUpdatesConversationUnreadState() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)

        try {
            val postsFlow = MutableStateFlow<List<PostWithSubscription>>(emptyList())
            val unreadConversationCountFlow = MutableStateFlow(0)

            val postsRepository = mock<PostsRepository>()
            val conversationsRepository = mock<ConversationsRepository>()
            doReturn(postsFlow).whenever(postsRepository).getPostsFlow()
            doReturn(unreadConversationCountFlow).whenever(conversationsRepository).getUnreadConversationCountFlow()

            val roverBadge = RoverBadge(
                postsRepository = postsRepository,
                conversationsRepository = conversationsRepository,
            )

            advanceUntilIdle()
            assertThat(roverBadge.newBadge.value, equalTo(null))

            unreadConversationCountFlow.value = 1

            advanceUntilIdle()
            assertThat(roverBadge.newBadge.value, equalTo("1"))

            unreadConversationCountFlow.value = 0

            advanceUntilIdle()
            assertThat(roverBadge.newBadge.value, equalTo(null))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun badgeCountsUnreadConversationsEvenWithoutHydratedReplies() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)

        try {
            val postsFlow = MutableStateFlow<List<PostWithSubscription>>(emptyList())
            val unreadConversationCountFlow = MutableStateFlow(1)

            val postsRepository = mock<PostsRepository>()
            val conversationsRepository = mock<ConversationsRepository>()
            doReturn(postsFlow).whenever(postsRepository).getPostsFlow()
            doReturn(unreadConversationCountFlow).whenever(conversationsRepository).getUnreadConversationCountFlow()

            val roverBadge = RoverBadge(
                postsRepository = postsRepository,
                conversationsRepository = conversationsRepository,
            )

            advanceUntilIdle()
            assertThat(roverBadge.newBadge.value, equalTo("1"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun badgeCombinesUnreadPostsAndUnreadConversations() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(mainDispatcher)

        try {
            val postsFlow = MutableStateFlow(
                listOf(
                    postWithSubscription(id = "post-1", isRead = false),
                    postWithSubscription(id = "post-2", isRead = false),
                    postWithSubscription(id = "post-3", isRead = true),
                )
            )
            val unreadConversationCountFlow = MutableStateFlow(2)

            val postsRepository = mock<PostsRepository>()
            val conversationsRepository = mock<ConversationsRepository>()
            doReturn(postsFlow).whenever(postsRepository).getPostsFlow()
            doReturn(unreadConversationCountFlow).whenever(conversationsRepository).getUnreadConversationCountFlow()

            val roverBadge = RoverBadge(
                postsRepository = postsRepository,
                conversationsRepository = conversationsRepository,
            )

            advanceUntilIdle()
            assertThat(roverBadge.newBadge.value, equalTo("4"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun postWithSubscription(id: String, isRead: Boolean): PostWithSubscription {
        return PostWithSubscription(
            post = io.rover.sdk.notifications.communicationhub.posts.PostEntity(
                id = id,
                subject = "Post $id",
                previewText = "Preview $id",
                receivedAt = 1_000L,
                url = "https://example.com/$id",
                isRead = isRead,
                coverImageURL = null,
                subscriptionId = null,
            ),
            subscription = null,
        )
    }
}
