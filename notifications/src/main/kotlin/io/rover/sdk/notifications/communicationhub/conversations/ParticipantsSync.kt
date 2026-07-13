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

package io.rover.sdk.notifications.communicationhub.conversations

import com.squareup.moshi.Moshi
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import io.rover.sdk.core.data.sync.SyncStandaloneParticipant
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.conversations.dto.ParticipantsSyncResponse
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.sync.HubResetCancellable
import io.rover.sdk.notifications.communicationhub.sync.HubResetJobTracker
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.sync.StaleHubSyncGenerationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Syncs the participants snapshot from the Engage API into the local `participants` table.
 *
 * Persistence is non-destructive: rows are merged via [ParticipantsDao.mergeParticipants], so a
 * sync may add participants or improve their fields but never deletes rows or nulls out existing
 * values. The server can legitimately return empty or partial participant lists (e.g. identity
 * resolution misses, broadcast-seed owners omitted), and a destructive replace would wipe good
 * local data. Deletion happens only through the Hub reset paths (HTTP 410 reset and
 * ConversationsRepository.clearConversationData).
 */
internal class ParticipantsSync(
    private val hubSyncCoordinator: HubSyncCoordinator,
    private val participantsDao: ParticipantsDao,
    private val transactionRunner: ConversationsTransactionRunner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncStandaloneParticipant, HubResetCancellable {
    constructor(
        engageApiService: EngageApiService,
        participantsDao: ParticipantsDao,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        hubSyncCoordinator = HubSyncCoordinator(engageApiService),
        participantsDao = participantsDao,
        transactionRunner = object : ConversationsTransactionRunner {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        },
        backgroundDispatcher = backgroundDispatcher,
    )

    private val participantsSyncResponseAdapter = Moshi.Builder()
        .build()
        .adapter(ParticipantsSyncResponse::class.java)
    private val activeSyncJobs = HubResetJobTracker(
        cancellationMessage = "Participants sync cancelled because Hub data was reset."
    )

    override suspend fun sync(): Boolean = activeSyncJobs.track {
        withContext(backgroundDispatcher) {
            try {
                log.i("Starting participants sync")
                val hubResponse = hubSyncCoordinator.getParticipants()
                val response = hubResponse.response

                if (!response.isSuccessful) {
                    log.e("Participants API request failed with code: ${response.code()}")
                    return@withContext false
                }

                val responseBody = response.body()?.string()
                if (responseBody == null) {
                    log.e("Empty response body from participants API")
                    return@withContext false
                }

                val syncResponse = participantsSyncResponseAdapter.fromJson(responseBody)
                    ?: throw IllegalArgumentException("Failed to parse participants response")
                transactionRunner.withTransaction {
                    hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                    participantsDao.mergeParticipants(syncResponse.participants.map { it.toEntity() })
                    hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                }

                log.i("Participants sync completed successfully")
                true
            } catch (resetRequired: SyncResetRequiredException) {
                throw resetRequired
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StaleHubSyncGenerationException) {
                log.d("Participants sync skipped because Hub data was reset during sync")
                false
            } catch (error: Exception) {
                log.e("Failed to sync participants: ${error.message}")
                false
            }
        }
    }

    override suspend fun cancelAndJoinHubResetInvalidatedWork() {
        activeSyncJobs.cancelAndJoinHubResetInvalidatedWork()
    }
}
