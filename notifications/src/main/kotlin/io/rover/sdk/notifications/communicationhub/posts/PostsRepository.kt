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

import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.posts.dto.PostItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class PostsRepository(
    private val postsDao: PostsDao,
    private val subscriptionsDao: SubscriptionsDao,
): PostsDataSource {

    suspend fun upsertPostFromDto(postItem: PostItem) {
        val existingPost = postsDao.getPostById(postItem.id)
        log.d("Inserting ${if (existingPost == null) "new" else "existing"} post with ID ${postItem.id}")
        // Subscription should generally already exist from subscription sync, but if new post
        // arrives from push with a new subscription that was published on the API since our last sync,
        // it won't yet exist.
        postItem.subscriptionID?.let { subscriptionId ->
            if (subscriptionsDao.getSubscriptionById(subscriptionId) == null) {
                log.i("Warning: Push post references a new subscription ID $subscriptionId that doesn't exist locally, creating a placeholder")

                // create placeholder subscription if needed:
                subscriptionsDao.upsertSubscription(
                    SubscriptionEntity(
                        id = subscriptionId,
                        name = "",
                        description = "",
                        // (opt-in being false on the placeholder won't cause delivery of opt-in
                        // messages to users who haven't opted-in, since that policy implemented
                        // server-side.)
                        optIn = false,
                        status = "published",
                        logoURL = null,
                    )
                )
            }
        }
        // Update or insert the post into the database, keeping the existing read state if set.
        postsDao.upsertPost(postItem.toEntity(existingPostIsRead = existingPost?.isRead ?: false))
    }

    override fun getPostsFlow(): Flow<List<PostWithSubscription>> {
        return postsDao.getAllPostsWithSubscriptions()
    }

    suspend fun getPostById(postId: String): PostEntity? = withContext(Dispatchers.IO) {
        postsDao.getPostById(postId)
    }

    suspend fun getPostWithSubscriptionById(postId: String): PostWithSubscription? = withContext(Dispatchers.IO) {
        postsDao.getPostWithSubscriptionById(postId)
    }

    override suspend fun markPostAsRead(postId: String) = withContext(Dispatchers.IO) {
        log.d("Marking post as read: $postId")
        postsDao.markPostAsRead(postId)
    }

    suspend fun getBadgeCount(): Int = withContext(Dispatchers.IO) {
        postsDao.getUnreadCount()
    }

    suspend fun savePostFromPush(postItem: PostItem): Unit = withContext(Dispatchers.IO) {
        try {
            log.i("Saving post from push notification: ${postItem.id}")

            upsertPostFromDto(postItem)

            log.i("Successfully saved post from push: ${postItem.id}")
        } catch (e: Exception) {
            log.e("Failed to save post from push: ${e.message}")
        }
    }
}
