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

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import io.rover.sdk.core.data.sync.SyncStandaloneParticipant
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsTransactionRunner
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.posts.dto.PostsSyncResponse
import io.rover.sdk.notifications.communicationhub.sync.HubResetCancellable
import io.rover.sdk.notifications.communicationhub.sync.HubResetJobTracker
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.sync.SyncStateDao
import io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity
import io.rover.sdk.notifications.communicationhub.sync.StaleHubSyncGenerationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

internal class PostsSync(
    private val hubSyncCoordinator: HubSyncCoordinator,
    private val postsRepository: PostsRepository,
    private val syncStateDao: SyncStateDao,
    private val deviceIdentification: DeviceIdentificationInterface,
    private val transactionRunner: ConversationsTransactionRunner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncStandaloneParticipant, HubResetCancellable {
    constructor(
        engageApiService: EngageApiService,
        postsRepository: PostsRepository,
        syncStateDao: SyncStateDao,
        deviceIdentification: DeviceIdentificationInterface,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        hubSyncCoordinator = HubSyncCoordinator(engageApiService),
        postsRepository = postsRepository,
        syncStateDao = syncStateDao,
        deviceIdentification = deviceIdentification,
        transactionRunner = object : ConversationsTransactionRunner {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        },
        backgroundDispatcher = backgroundDispatcher,
    )

    companion object {
        private const val POSTS_ENTITY_TYPE = "posts"
    }

    private val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()
    private val postsSyncResponseAdapter = moshi.adapter(PostsSyncResponse::class.java)
    private val activeSyncJobs = HubResetJobTracker(
        cancellationMessage = "Posts sync cancelled because Hub data was reset."
    )

    override suspend fun sync(): Boolean = activeSyncJobs.track {
        withContext(backgroundDispatcher) {
            try {
                log.i("Starting posts sync")
                val cursor = syncStateDao.getSyncState(POSTS_ENTITY_TYPE)?.forwardCursor
                val deviceId = deviceIdentification.installationIdentifier
                val result = syncPosts(deviceId = deviceId, cursor = cursor)
                val success = result.hadPosts || result.completedSuccessfully
                log.i("Posts sync completed, success: $success, hadPosts: ${result.hadPosts}")
                success
            } catch (resetRequired: SyncResetRequiredException) {
                throw resetRequired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StaleHubSyncGenerationException) {
                log.d("Posts sync skipped because Hub data was reset during sync")
                false
            } catch (error: Exception) {
                log.e("Failed to sync posts: ${error.message}")
                false
            }
        }
    }

    override suspend fun cancelAndJoinHubResetInvalidatedWork() {
        activeSyncJobs.cancelAndJoinHubResetInvalidatedWork()
    }

    private suspend fun syncPosts(deviceId: String, cursor: String?): PageSyncResult = withContext(backgroundDispatcher) {
        try {
            log.d("Fetching posts page with cursor: $cursor")
            val hubResponse = hubSyncCoordinator.getPosts(deviceId, cursor)
            val response = hubResponse.response

            if (!response.isSuccessful) {
                log.e("Posts API request failed with code: ${response.code()}")
                return@withContext PageSyncResult(hadPosts = false, completedSuccessfully = false)
            }

            val responseBody = response.body()?.string()
            if (responseBody == null) {
                log.e("Empty response body from posts API")
                return@withContext PageSyncResult(hadPosts = false, completedSuccessfully = false)
            }

            val syncResponse = postsSyncResponseAdapter.fromJson(responseBody)
                ?: throw IllegalArgumentException("Failed to parse posts response")
            log.d("Parsed ${syncResponse.posts.size} posts from page")

            transactionRunner.withTransaction {
                hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                syncResponse.posts.forEach { postItem ->
                    postsRepository.upsertPostFromDto(postItem)
                }

                syncStateDao.replaceSyncState(
                    SyncStateEntity(
                        POSTS_ENTITY_TYPE,
                        forwardCursor = syncResponse.nextCursor,
                        backwardCursor = null,
                        historyComplete = false,
                    )
                )
                hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
            }

            val hasPostsInPage = syncResponse.posts.isNotEmpty()
            if (!syncResponse.hasMore) {
                log.i("No more pages, posts sync complete")
                return@withContext PageSyncResult(
                    hadPosts = hasPostsInPage,
                    completedSuccessfully = true,
                )
            }

            log.i("More pages available, fetching next page")
            val nextPage = syncPosts(deviceId, syncResponse.nextCursor)
            PageSyncResult(
                hadPosts = hasPostsInPage || nextPage.hadPosts,
                completedSuccessfully = nextPage.completedSuccessfully,
            )
        } catch (resetRequired: SyncResetRequiredException) {
            throw resetRequired
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (stale: StaleHubSyncGenerationException) {
            throw stale
        } catch (error: Exception) {
            log.e("Failed to sync posts: ${error.message}")
            PageSyncResult(hadPosts = false, completedSuccessfully = false)
        }
    }

    private data class PageSyncResult(
        val hadPosts: Boolean,
        val completedSuccessfully: Boolean,
    )
}
