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
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsTransactionRunner
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.posts.dto.SubscriptionsSyncResponse
import io.rover.sdk.notifications.communicationhub.sync.HubResetCancellable
import io.rover.sdk.notifications.communicationhub.sync.HubResetJobTracker
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.sync.StaleHubSyncGenerationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

internal class SubscriptionsSync(
    private val hubSyncCoordinator: HubSyncCoordinator,
    private val subscriptionsDao: SubscriptionsDao,
    private val transactionRunner: ConversationsTransactionRunner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncStandaloneParticipant, HubResetCancellable {
    constructor(
        engageApiService: EngageApiService,
        subscriptionsDao: SubscriptionsDao,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        hubSyncCoordinator = HubSyncCoordinator(engageApiService),
        subscriptionsDao = subscriptionsDao,
        transactionRunner = object : ConversationsTransactionRunner {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        },
        backgroundDispatcher = backgroundDispatcher,
    )

    private val moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()
    private val subscriptionsSyncResponseAdapter = moshi.adapter(SubscriptionsSyncResponse::class.java)
    private val activeSyncJobs = HubResetJobTracker(
        cancellationMessage = "Subscriptions sync cancelled because Hub data was reset."
    )

    override suspend fun sync(): Boolean = activeSyncJobs.track {
        withContext(backgroundDispatcher) {
            try {
                log.i("Starting subscriptions sync")
                val hubResponse = hubSyncCoordinator.getSubscriptions()
                val response = hubResponse.response

                if (!response.isSuccessful) {
                    log.e("Subscriptions API request failed with code: ${response.code()}")
                    return@withContext false
                }

                val responseBody = response.body()?.string()
                if (responseBody == null) {
                    log.e("Empty response body from subscriptions API")
                    return@withContext false
                }

                val syncResponse = subscriptionsSyncResponseAdapter.fromJson(responseBody)
                    ?: throw IllegalArgumentException("Failed to parse subscriptions response")
                transactionRunner.withTransaction {
                    hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                    subscriptionsDao.upsertSubscriptions(syncResponse.subscriptions.map { it.toEntity() })
                    hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                }

                log.i("Subscriptions sync completed successfully")
                true
            } catch (resetRequired: SyncResetRequiredException) {
                throw resetRequired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StaleHubSyncGenerationException) {
                log.d("Subscriptions sync skipped because Hub data was reset during sync")
                false
            } catch (error: Exception) {
                log.e("Failed to sync subscriptions: ${error.message}")
                false
            }
        }
    }

    override suspend fun cancelAndJoinHubResetInvalidatedWork() {
        activeSyncJobs.cancelAndJoinHubResetInvalidatedWork()
    }
}
