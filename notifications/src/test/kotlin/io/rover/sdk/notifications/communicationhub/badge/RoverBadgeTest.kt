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
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.config.HubConfig
import io.rover.sdk.core.data.config.RoverConfig
import io.rover.sdk.notifications.communicationhub.InMemoryLocalStorage
import io.rover.sdk.notifications.communicationhub.InboxSeenWatermark
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ConversationTestFixtures
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.posts.PostsRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun disabledInboxSuppressesUnreadItems() = withBadgeFixture(
        isInboxEnabled = false,
        initialConversations = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 2_000L),
        ),
    ) { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "post-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(0))
    }

    @Test
    fun disablingInboxImmediatelyClearsExistingBadge() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "post-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
        )
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 2_000L),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo("2"))

        fixture.setInboxEnabled(false)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(0))
    }

    @Test
    fun reenablingInboxRestoresBadgeFromStoredUnreadItems() = withBadgeFixture(
        isInboxEnabled = false,
    ) { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "post-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
        )
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 2_000L),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo(null))

        fixture.setInboxEnabled(true)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("2"))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(2))
    }

    @Test
    fun recomputesBadgeWhenPushUpdatesConversationUnreadState() = withBadgeFixture { fixture ->
        assertThat(fixture.badge.newBadge.value, equalTo(null))

        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 1_000L),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo("1"))

        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "convo-1",
                lastReplyAt = WATERMARK_MILLIS + 1_000L,
                lastReadAt = WATERMARK_MILLIS + 1_000L,
            ),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo(null))
    }

    @Test
    fun badgeCountsUnreadConversationsEvenWithoutHydratedReplies() = withBadgeFixture(
        initialConversations = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 1_000L),
        ),
    ) { fixture ->
        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun outboundReplyAfterWatermarkDoesNotBadgeButAFollowingIncomingReplyDoes() = withBadgeFixture { fixture ->
        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "convo-1",
                lastReplyAt = WATERMARK_MILLIS,
                lastIncomingReplyAt = WATERMARK_MILLIS,
            ),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo(null))

        // The optimistic reply insert itself does not update the conversation row. Once sync
        // confirms it, lastReplyAt advances, while the inbound-only timestamp stays put.
        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "convo-1",
                lastReplyAt = WATERMARK_MILLIS + 1_000L,
                lastIncomingReplyAt = WATERMARK_MILLIS,
            ),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo(null))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(0))

        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "convo-1",
                lastReplyAt = WATERMARK_MILLIS + 2_000L,
                lastIncomingReplyAt = WATERMARK_MILLIS + 2_000L,
            ),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun conversationWithoutAnIncomingTimestampDoesNotBadge() = withBadgeFixture { fixture ->
        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "direction-unknown",
                lastReplyAt = WATERMARK_MILLIS + 1_000L,
                lastIncomingReplyAt = null,
            ),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
    }

    @Test
    fun conversationsWithActivityBeforeWatermarkDoNotCountEvenWhenUnread() = withBadgeFixture { fixture ->
        // The upgrade/410 backlog case: unread conversations whose activity predates the watermark
        // must not badge — the same rule that keeps a lifetime of unread posts out of the badge.
        fixture.conversationsFlow.value = listOf(
            conversation(id = "unread-old", lastReplyAt = WATERMARK_MILLIS - 1_000L),
            // exactly at the watermark counts as seen (the comparison is strictly greater)
            conversation(id = "at-watermark", lastReplyAt = WATERMARK_MILLIS),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
    }

    @Test
    fun readConversationsWithActivityAfterWatermarkDoNotCount() = withBadgeFixture { fixture ->
        fixture.conversationsFlow.value = listOf(
            conversation(id = "unread-new", lastReplyAt = WATERMARK_MILLIS + 1_000L),
            conversation(
                id = "read-new",
                lastReplyAt = WATERMARK_MILLIS + 2_000L,
                lastReadAt = WATERMARK_MILLIS + 2_000L,
            ),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun aConversationWhosePushOnlyUpdatedTheIncomingReplyTimestampStillBadges() = withBadgeFixture { fixture ->
        // The push path merges an incoming reply's timestamp into lastIncomingReplyAt without
        // touching lastReplyAt, so the newest activity can legitimately live on
        // lastIncomingReplyAt alone.
        fixture.conversationsFlow.value = listOf(
            conversation(
                id = "pushed",
                lastReplyAt = WATERMARK_MILLIS - 1_000L,
                lastIncomingReplyAt = WATERMARK_MILLIS + 1_000L,
            ),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun postsDeliveredBeforeWatermarkDoNotCountEvenWhenUnread() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "unread-old", receivedAt = WATERMARK_MILLIS - 1_000L, isRead = false),
            post(id = "read-old", receivedAt = WATERMARK_MILLIS - 500L, isRead = true),
            // exactly at the watermark counts as seen (the comparison is strictly greater)
            post(id = "at-watermark", receivedAt = WATERMARK_MILLIS, isRead = false),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
    }

    @Test
    fun unreadPostsDeliveredAfterWatermarkCount() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "unread-new-1", receivedAt = WATERMARK_MILLIS + 1L, isRead = false),
            post(id = "unread-new-2", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = false),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("2"))
    }

    @Test
    fun readPostsDeliveredAfterWatermarkDoNotCount() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "unread-new", receivedAt = WATERMARK_MILLIS + 1L, isRead = false),
            post(id = "read-new", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = true),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun readingAPostFromADeepLinkDecrementsTheBadge() = withBadgeFixture { fixture ->
        // Two posts arrive by push while the user never opens the inbox, so the watermark stays
        // put; both badge.
        fixture.postsFlow.value = listOf(
            post(id = "new-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
            post(id = "new-2", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = false),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo("2"))

        // Tapping one push opens the detail screen, which marks that post (and only that post)
        // read. The other, older post must keep badging.
        fixture.postsFlow.value = listOf(
            post(id = "new-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
            post(id = "new-2", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = true),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun badgeSumsUnreadPostsAndUnreadConversationsAfterWatermark() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "new-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
            post(id = "new-2", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = true),
            post(id = "old-1", receivedAt = WATERMARK_MILLIS - 1_000L, isRead = false),
        )
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-new", lastReplyAt = WATERMARK_MILLIS + 1_500L),
            conversation(id = "convo-old", lastReplyAt = WATERMARK_MILLIS - 1_500L),
            conversation(
                id = "convo-read",
                lastReplyAt = WATERMARK_MILLIS + 3_000L,
                lastReadAt = WATERMARK_MILLIS + 3_000L,
            ),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("2"))
    }

    @Test
    fun markingTheInboxSeenClearsTheWholeBadge() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = listOf(
            post(id = "new-1", receivedAt = WATERMARK_MILLIS + 1_000L, isRead = false),
            post(id = "new-2", receivedAt = WATERMARK_MILLIS + 2_000L, isRead = false),
        )
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 3_000L),
        )
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo("3"))

        // Opening the inbox advances the watermark past every stored item — posts and
        // conversations clear together in one gesture, without opening anything.
        fixture.markInboxSeenAt(WATERMARK_MILLIS + 5_000L)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo(null))
    }

    @Test
    fun aNewReplyAfterTheInboxWasSeenReBadgesTheConversation() = withBadgeFixture { fixture ->
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 1_000L),
        )
        fixture.markInboxSeenAt(WATERMARK_MILLIS + 1_000L)
        advanceUntilIdle()
        assertThat(fixture.badge.newBadge.value, equalTo(null))

        // A reply lands after the visit: the conversation's activity moves past the watermark and
        // it badges again, even though it already existed before the visit.
        fixture.conversationsFlow.value = listOf(
            conversation(id = "convo-1", lastReplyAt = WATERMARK_MILLIS + 4_000L),
        )
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("1"))
    }

    @Test
    fun nineNewItemsDisplayAsTheNumber() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = newPosts(count = 9)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("9"))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(9))
    }

    @Test
    fun tenNewItemsDisplayAsNinePlusAndCapThePublisherAtNine() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = newPosts(count = 10)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("9+"))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(9))
    }

    @Test
    fun capAppliesToTheCombinedPostAndConversationCount() = withBadgeFixture { fixture ->
        fixture.postsFlow.value = newPosts(count = 7)
        fixture.conversationsFlow.value = newConversations(count = 40)
        advanceUntilIdle()

        assertThat(fixture.badge.newBadge.value, equalTo("9+"))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(9))
    }

    @Test
    fun publisherReportsZeroWhenThereIsNothingNew() = withBadgeFixture { fixture ->
        assertThat(fixture.badge.newBadge.value, equalTo(null))
        assertThat(fixture.badge.newBadgePublisher().asFlow().first(), equalTo(0))
    }

    private class BadgeFixture(
        val badge: RoverBadge,
        val postsFlow: MutableStateFlow<List<PostWithSubscription>>,
        val conversationsFlow: MutableStateFlow<List<ConversationEntity>>,
        /** Marks the inbox seen as of the given epoch-millis instant. */
        val markInboxSeenAt: (Long) -> Unit,
        /** Publishes a backend config update with the requested inbox state. */
        val setInboxEnabled: (Boolean) -> Unit,
    )

    /**
     * Builds a [RoverBadge] wired to controllable posts, conversations, and watermark sources, with
     * the watermark pinned at [WATERMARK_MILLIS] so tests can express item timestamps relative to it.
     */
    private fun withBadgeFixture(
        isInboxEnabled: Boolean = true,
        initialConversations: List<ConversationEntity> = emptyList(),
        body: suspend TestScope.(BadgeFixture) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        try {
            val postsFlow = MutableStateFlow<List<PostWithSubscription>>(emptyList())
            val conversationsFlow = MutableStateFlow(initialConversations)

            val postsRepository = mock<PostsRepository>()
            val conversationsRepository = mock<ConversationsDataSource>()
            doReturn(postsFlow).whenever(postsRepository).getPostsFlow()
            doReturn(conversationsFlow).whenever(conversationsRepository).getConversationsFlow()

            val clock = Instant.ofEpochMilli(WATERMARK_MILLIS)
            val watermark = InboxSeenWatermark(
                InMemoryLocalStorage(),
                now = { clock },
            )
            val configManager = ConfigManager(InMemoryLocalStorage()).apply {
                updateFromBackend(
                    RoverConfig(hub = HubConfig(isInboxEnabled = isInboxEnabled)),
                )
            }

            val fixture = BadgeFixture(
                badge = RoverBadge(
                    postsRepository = postsRepository,
                    conversationsRepository = conversationsRepository,
                    inboxSeenWatermark = watermark,
                    configManager = configManager,
                ),
                postsFlow = postsFlow,
                conversationsFlow = conversationsFlow,
                markInboxSeenAt = { epochMillis ->
                    // The watermark is written from the newest item activity the inbox has shown,
                    // never from the device clock.
                    watermark.markSeen(epochMillis)
                },
                setInboxEnabled = { enabled ->
                    configManager.updateFromBackend(
                        RoverConfig(hub = HubConfig(isInboxEnabled = enabled)),
                    )
                },
            )

            advanceUntilIdle()
            body(fixture)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun newPosts(count: Int): List<PostWithSubscription> = (1..count).map { index ->
        post(id = "new-$index", receivedAt = WATERMARK_MILLIS + index, isRead = false)
    }

    private fun newConversations(count: Int): List<ConversationEntity> = (1..count).map { index ->
        conversation(id = "convo-new-$index", lastReplyAt = WATERMARK_MILLIS + index)
    }

    private fun post(id: String, receivedAt: Long, isRead: Boolean): PostWithSubscription {
        return PostWithSubscription(
            post = PostEntity(
                id = id,
                subject = "Post $id",
                previewText = "Preview $id",
                receivedAt = receivedAt,
                url = "https://example.com/$id",
                isRead = isRead,
                coverImageURL = null,
                subscriptionId = null,
            ),
            subscription = null,
        )
    }

    private fun conversation(
        id: String,
        lastReplyAt: Long,
        lastIncomingReplyAt: Long? = lastReplyAt,
        lastReadAt: Long? = null,
    ): ConversationEntity = ConversationTestFixtures.conversation(
        id = id,
        lastReplyAt = lastReplyAt,
        lastIncomingReplyAt = lastIncomingReplyAt,
        lastReadAt = lastReadAt,
    )

    private companion object {
        /** The instant the inbox was last seen, in epoch milliseconds. */
        const val WATERMARK_MILLIS = 1_000_000L
    }
}
