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

package io.rover.sdk.notifications.communicationhub.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.rover.sdk.core.data.sync.SyncStandaloneParticipant
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.notifications.communicationhub.data.database.dao.CursorsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.PostsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.SubscriptionsDao
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import io.rover.sdk.notifications.communicationhub.data.database.entities.SubscriptionEntity
import io.rover.sdk.notifications.communicationhub.data.dto.PostItem
import io.rover.sdk.notifications.communicationhub.data.dto.PostsSyncResponse
import io.rover.sdk.notifications.communicationhub.data.dto.SubscriptionsSyncResponse
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

internal class CommHubRepository(
    private val engageApiService: EngageApiService,
    private val postsDao: PostsDao,
    private val subscriptionsDao: SubscriptionsDao,
    private val cursorsDao: CursorsDao,
    private val deviceIdentification: DeviceIdentificationInterface
): SyncStandaloneParticipant {
    companion object {
        private const val POSTS_ENTITY_TYPE = "posts"
    }
    
    private val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()
    
    private val subscriptionsSyncResponseAdapter = moshi.adapter(SubscriptionsSyncResponse::class.java)
    private val postsSyncResponseAdapter = moshi.adapter(PostsSyncResponse::class.java)

    private val activeSyncJob = AtomicReference<Deferred<Boolean>?>(null)

    override suspend fun sync(): Boolean {
        // task coalescing: if a sync is already in progress, wait for it to complete instead of
        // dispatching another.
        val job = activeSyncJob.updateAndGet { current ->
            current ?: CoroutineScope(Dispatchers.IO).async { performSync() }
        }!!

        return try {
            job.await()
        } finally {
            activeSyncJob.compareAndSet(job, null)
        }
    }

    private suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First sync subscriptions
            val subscriptionsResult = syncSubscriptions()
            if (!subscriptionsResult) {
                log.w("Failed to sync subscriptions, continuing with posts sync")
            }
            
            log.i("Starting posts sync")
            val cursor = cursorsDao.getCursor(POSTS_ENTITY_TYPE)
            log.d("Current cursor for posts sync: $cursor")
            val deviceId = deviceIdentification.installationIdentifier
            
            log.d("Making API request to get posts for device: $deviceId")
            val response = engageApiService.getPosts(deviceId, cursor)
            
            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    log.i("Successfully received posts sync response")
                    val syncResponse = postsSyncResponseAdapter.fromJson(responseBody)
                        ?: throw IllegalArgumentException("Failed to parse posts response")
                    log.d("Parsed ${syncResponse.posts.size} posts from response")
                    
                    // Save posts and subscriptions
                    log.d("Processing ${syncResponse.posts.size} posts")
                    syncResponse.posts.forEach { postItem ->
                        log.d("Processing post ${postItem.id}")
                        upsertPostFromDto(postItem)
                    }

                    log.d("Updating cursor to: ${syncResponse.nextCursor}")
                    cursorsDao.updateCursor(POSTS_ENTITY_TYPE, syncResponse.nextCursor)
                    
                    // Continue syncing if more pages available
                    if (syncResponse.hasMore) {
                        log.i("More pages available, continuing sync")
                        performSync()
                    } else {
                        log.i("Posts sync completed successfully")
                        true
                    }
                } else {
                    log.e("Empty response body from API")
                    false
                }
            } else {
                log.e("API request failed with code: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            log.e("Failed to sync posts: ${e.message}")
            false
        }
    }

    private suspend fun syncSubscriptions(): Boolean = withContext(Dispatchers.IO) {
        try {
            log.i("Starting subscriptions sync")

            log.d("Making API request to get subscriptions")
            val response = engageApiService.getSubscriptions()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    log.i("Successfully received subscriptions sync response")
                    val syncResponse = subscriptionsSyncResponseAdapter.fromJson(responseBody)
                        ?: throw IllegalArgumentException("Failed to parse subscriptions response")
                    log.d("Parsed ${syncResponse.subscriptions.size} subscriptions from response")

                    // Upsert subscriptions (update existing, insert new ones)
                    val subscriptionEntities = syncResponse.subscriptions.map { it.toEntity() }
                    subscriptionsDao.upsertSubscriptions(subscriptionEntities)

                    log.i("Subscriptions sync completed successfully")
                    true
                } else {
                    log.e("Empty response body from subscriptions API")
                    false
                }
            } else {
                log.e("Subscriptions API request failed with code: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            log.e("Failed to sync subscriptions: ${e.message}")
            false
        }
    }

    /// For the given DTO, upsert it into the database.
    suspend fun upsertPostFromDto(postItem: PostItem) {
        val existingPost = postsDao.getPostById(postItem.id)
        log.d("Inserting ${if (existingPost == null) "existing" else "new"} post with ID${postItem.id}")
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
                        status = "published"
                    )
                )
            }
        }
        // Update or insert the post into the database, keeping the existing read state if set.
        postsDao.upsertPost(postItem.toEntity(existingPostIsRead = existingPost?.isRead ?: false))
    }
    
    fun getPostsFlow(): Flow<List<PostWithSubscription>> {
        return postsDao.getAllPostsWithSubscriptions()
    }
    
    suspend fun getPostById(postId: String): PostEntity? = withContext(Dispatchers.IO) {
        postsDao.getPostById(postId)
    }
    
    suspend fun markPostAsRead(postId: String) = withContext(Dispatchers.IO) {
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