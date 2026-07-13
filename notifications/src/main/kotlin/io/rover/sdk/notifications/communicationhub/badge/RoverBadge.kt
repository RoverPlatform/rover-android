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

import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsRepository
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
 */
internal class RoverBadge(
    private val postsRepository: PostsRepository,
    private val conversationsRepository: ConversationsRepository,
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
        postsRepository.getPostsFlow()
            .combine(conversationsRepository.getUnreadConversationCountFlow()) { postsWithSubscriptions, unreadConversations ->
                val unreadPosts = postsWithSubscriptions.count { !it.post.isRead }
                unreadPosts + unreadConversations
            }
            .distinctUntilChanged()
            .onEach { unreadCount ->
                log.v("Rover badge count updated: $unreadCount")
                val badgeValue = when {
                    unreadCount <= 0 -> null
                    unreadCount > 99 -> "99+"
                    else -> unreadCount.toString()
                }

                _newBadgePublisherFlow.value = unreadCount
                _newBadgeFlow.value = badgeValue
            }
            .launchIn(scope)
    }
}
