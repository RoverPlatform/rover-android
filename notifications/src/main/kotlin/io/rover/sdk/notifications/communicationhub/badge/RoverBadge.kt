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

import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.InboxSeenWatermark
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDataSource
import io.rover.sdk.notifications.communicationhub.conversations.badgeActivityAt
import io.rover.sdk.notifications.communicationhub.conversations.isUnread
import io.rover.sdk.notifications.communicationhub.posts.PostsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Observable badge implementation for Rover content, such as posts.
 *
 * This class observes the Rover Engage API repository for changes and provides
 * reactive badge count information that can be consumed by Jetpack Compose or traditional Views.
 *
 * Posts and conversations contribute by the same rule: an item counts when it is *both* unread
 * *and* has activity after the [InboxSeenWatermark] (the last time the user looked at the inbox) —
 * `receivedAt` for posts, [badgeActivityAt] for conversations. Conversation activity is
 * inbound-only so sending a reply cannot badge the user's own activity. See [InboxSeenWatermark]
 * for why the watermark is needed on top of read state.
 */
internal class RoverBadge(
    private val postsRepository: PostsRepository,
    private val conversationsRepository: ConversationsDataSource,
    private val inboxSeenWatermark: InboxSeenWatermark,
    private val configManager: ConfigManager,
) : RoverBadgeInterface {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private val _newBadgeFlow = MutableStateFlow<String?>(null)
    override val newBadge: StateFlow<String?> = _newBadgeFlow.asStateFlow()
    
    init {
        observePostsChanges()
    }

    private val _newBadgePublisherFlow = MutableStateFlow<Int>(0)
    override fun newBadgePublisher(): Publisher<Int> = _newBadgePublisherFlow.asPublisher(scope.coroutineContext)
    
    private fun observePostsChanges() {
        combine(
            postsRepository.getPostsFlow(),
            conversationsRepository.getConversationsFlow(),
            inboxSeenWatermark.seenAt,
            configManager.config,
        ) { postsWithSubscriptions, conversations, seenAtMillis, config ->
            if (!config.hub.isInboxEnabled) {
                return@combine 0
            }

            // Strictly greater than: an item delivered in the same millisecond the user opened the
            // inbox counts as seen. Read items never count, so opening a single post or
            // conversation from a push tap or deep link removes exactly that item from the badge.
            val newPosts = postsWithSubscriptions.count {
                it.post.receivedAt > seenAtMillis && !it.post.isRead
            }
            val newConversations = conversations.count {
                val badgeActivityAt = it.badgeActivityAt
                it.isUnread && badgeActivityAt != null && badgeActivityAt > seenAtMillis
            }
            newPosts + newConversations
        }
            .distinctUntilChanged()
            .onEach { newCount ->
                log.v("Rover badge count updated: $newCount")
                val badgeValue = when {
                    newCount <= 0 -> null
                    newCount > MAX_DISPLAYED_COUNT -> "$MAX_DISPLAYED_COUNT+"
                    else -> newCount.toString()
                }

                _newBadgePublisherFlow.value = newCount.coerceAtMost(MAX_DISPLAYED_COUNT)
                _newBadgeFlow.value = badgeValue
            }
            .launchIn(scope)
    }

    private companion object {
        /**
         * Counts above this are displayed as "9+" (and clamped to 9 on the numeric publisher).
         */
        const val MAX_DISPLAYED_COUNT = 9
    }
}
