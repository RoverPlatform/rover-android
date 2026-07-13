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
import io.rover.sdk.notifications.communicationhub.conversations.dto.ConversationSyncPage
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.sync.HubResetCancellable
import io.rover.sdk.notifications.communicationhub.sync.HubResetJobTracker
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.sync.StaleHubSyncGenerationException
import io.rover.sdk.notifications.communicationhub.sync.SyncStateDao
import io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface ConversationsHistorySync {
    suspend fun triggerEagerBackwardHistoryBackfill()

    /**
     * Forward-only refresh of the conversation list, for UI callers that poll while the
     * messages list is on screen. Unlike [triggerEagerBackwardHistoryBackfill] it never
     * walks backward history.
     */
    suspend fun syncForward()
}

/**
 * Owns global conversation-list synchronization for Communication Hub.
 *
 * The conversation sync strategy is intentionally split into two layers:
 *
 * 1. Global conversation-list sync lives here.
 *    This includes the standalone sync participant used by the global
 *    [io.rover.sdk.core.data.sync.SyncCoordinator], the forward pagination loop for keeping the
 *    local conversation list current, and the eager backward-history backfill used when the UI
 *    needs to recover older conversation rows that are not yet cached locally.
 *
 * 2. Conversation-detail reply sync stays in [ConversationsRepository].
 *    Reply bootstrap, reply polling, older-reply pagination, read checkpoints, and send flows are
 *    all tied to an individual conversation screen's lifecycle, so they remain repository-owned
 *    instead of being folded into the global standalone sync participant.
 *
 * In practice this means:
 *
 * - [performSync] does a forward-only refresh of the conversation list.
 *   It is used by the global sync coordinator alongside posts, subscriptions, and participants.
 *
 * - [syncForward] exposes the same forward-only refresh directly to UI callers, used by the
 *   messages list to poll for new conversation activity while it is on screen.
 *
 * - [triggerEagerBackwardHistoryBackfill] is a targeted recovery path for UI callers.
 *   It first runs the same forward refresh to catch up recent changes, then walks backward using
 *   `before` cursors until history is complete or no more pages are available.
 *
 * - 410 responses are handled as a conversation-domain reset.
 *   When the server indicates the local conversation dataset is no longer valid, this class clears
 *   cached conversation/reply/participant rows via repository-owned reset helpers, cancels reply
 *   polling, and signals the UI to dismiss any invalidated conversation detail views.
 *
 * [ConversationsHistorySync] exists so UI-facing code can depend on the special backfill behavior
 * without depending on repository internals or reintroducing global sync ownership into
 * [ConversationsRepository].
 */
internal class ConversationsSync(
    private val conversationsRepository: ConversationsRepository,
    private val hubSyncCoordinator: HubSyncCoordinator,
    private val conversationsDao: ConversationsDao,
    private val participantsDao: ParticipantsDao,
    private val syncStateDao: SyncStateDao,
    private val transactionRunner: ConversationsTransactionRunner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SyncStandaloneParticipant, ConversationsHistorySync, HubResetCancellable {
    constructor(
        conversationsRepository: ConversationsRepository,
        engageApiService: EngageApiService,
        conversationsDao: ConversationsDao,
        participantsDao: ParticipantsDao,
        syncStateDao: SyncStateDao,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        conversationsRepository = conversationsRepository,
        hubSyncCoordinator = HubSyncCoordinator(engageApiService),
        conversationsDao = conversationsDao,
        participantsDao = participantsDao,
        syncStateDao = syncStateDao,
        transactionRunner = object : ConversationsTransactionRunner {
            override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
        },
        backgroundDispatcher = backgroundDispatcher,
    )

    companion object {
        private const val CONVERSATIONS_SYNC_KEY = "conversations"
    }

    private val conversationsSyncPageAdapter = Moshi.Builder()
        .build()
        .adapter(ConversationSyncPage::class.java)

    private val activeJobs = HubResetJobTracker(
        cancellationMessage = "Conversations sync cancelled because Hub data was reset."
    )

    // Serializes the cursor read → fetch → persist page loops. The coordinator-driven sync(),
    // the messages list's syncForward() poll, and triggerEagerBackwardHistoryBackfill() can
    // otherwise run concurrently; each reads sync-state cursors before its HTTP request and
    // writes them after, so an older response completing last could overwrite a newer cursor
    // or revert historyComplete.
    private val syncStateMutex = Mutex()

    override suspend fun sync(): Boolean = activeJobs.track {
        withContext(backgroundDispatcher) {
            try {
                // Flush queued outbound replies app-wide so a failed send retries on every sync
                // cycle, not only while its conversation detail screen is open. The result is
                // ignored: a reply that still can't send is expected and must not fail the sync;
                // reset (410) and cancellation still propagate as exceptions.
                conversationsRepository.requestFlush()
                runConversationsForwardSync()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (resetRequired: SyncResetRequiredException) {
                throw resetRequired
            } catch (_: StaleHubSyncGenerationException) {
                log.d("Conversations sync skipped because Hub data was reset during sync")
                false
            } catch (error: Exception) {
                log.e("Conversations sync failed: ${error.message}")
                false
            }
        }
    }

    override suspend fun syncForward() {
        activeJobs.track {
            try {
                withContext(backgroundDispatcher) {
                    runConversationsForwardSync()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (resetRequired: SyncResetRequiredException) {
                handleServerRequestedReset("syncForward", resetRequired)
            } catch (_: StaleHubSyncGenerationException) {
                log.d("Conversations forward sync skipped because Hub data was reset during sync")
            }
        }
    }

    override suspend fun triggerEagerBackwardHistoryBackfill() {
        conversationsRepository.runConversationHistoryBackfillLock {
            activeJobs.track {
                log.i("triggerEagerBackwardHistoryBackfill: starting")
                try {
                    withContext(backgroundDispatcher) {
                        runConversationsForwardSync()
                        runConversationsBackwardBackfill()
                    }
                    log.i("triggerEagerBackwardHistoryBackfill: completed successfully")
                } catch (error: CancellationException) {
                    throw error
                } catch (resetRequired: SyncResetRequiredException) {
                    handleServerRequestedReset("triggerEagerBackwardHistoryBackfill", resetRequired)
                } catch (error: Exception) {
                    log.e("triggerEagerBackwardHistoryBackfill: failed with ${error::class.simpleName}: ${error.message}")
                    throw error
                }
            }
        }
    }

    /**
     * Server-requested reset (HTTP 410) recovery for the UI-facing entry points, which bypass
     * the global sync coordinator and so must tear down local conversation state themselves.
     * [sync] deliberately rethrows [SyncResetRequiredException] instead: on that path the
     * coordinator owns reset handling.
     */
    private suspend fun handleServerRequestedReset(
        operationName: String,
        cause: SyncResetRequiredException,
    ): Nothing {
        activeJobs.untrackCurrentJob()
        hubSyncCoordinator.resetAfterSyncCancellation()
        if (!hubSyncCoordinator.resetsDatabase) {
            conversationsRepository.clearConversationData()
            conversationsRepository.onHubReset()
        }
        log.e("$operationName: failed because server requested reset")
        throw IllegalStateException(
            "Failed to sync conversations: server has indicated that reset is required (HTTP 410)",
            cause,
        )
    }

    override suspend fun cancelAndJoinHubResetInvalidatedWork() {
        activeJobs.cancelAndJoinHubResetInvalidatedWork()
    }

    private suspend fun runConversationsForwardSync() = syncStateMutex.withLock {
        log.i("Starting conversations forward sync")
        var cursor = syncStateDao.getSyncState(CONVERSATIONS_SYNC_KEY)?.forwardCursor
        log.d("Current forward cursor for conversations sync: $cursor")

        do {
            val existingState = syncStateDao.getSyncState(CONVERSATIONS_SYNC_KEY)
            val page = fetchConversationsPage(cursor = cursor, before = null)
            persistConversationsPage(
                page = page.page,
                generation = page.generation,
                historyComplete = existingState?.historyComplete == true,
                preserveForwardCursorWhenNull = false,
                preserveBackwardCursorWhenNull = true,
            )
            cursor = page.page.nextCursor
            if (page.page.hasMore && cursor != null) {
                log.d("More conversation pages available, fetching next page")
            }
        } while (page.page.hasMore && cursor != null)

        log.i("Conversations forward sync complete")
    }

    private suspend fun runConversationsBackwardBackfill() = syncStateMutex.withLock {
        val existingState = syncStateDao.getSyncState(CONVERSATIONS_SYNC_KEY)
        if (existingState?.historyComplete == true) {
            log.d("Conversations backward backfill already complete, skipping")
            return
        }

        log.i("Starting conversations backward backfill")
        var before = existingState?.backwardCursor
        if (before == null) {
            log.i("No backward cursor, marking conversations history complete")
            existingState?.let {
                syncStateDao.upsertSyncState(it.copy(historyComplete = true))
            }
            return
        }
        var hasMore = true

        while (hasMore) {
            log.d("Fetching conversations backward page with cursor: $before")
            val page = fetchConversationsPage(cursor = null, before = before)
            hasMore = page.page.hasMore
            val canContinueBackfill = hasMore && page.page.backwardCursor != null
            persistConversationsPage(
                page = page.page,
                generation = page.generation,
                historyComplete = !canContinueBackfill,
                preserveForwardCursorWhenNull = true,
                preserveBackwardCursorWhenNull = false,
            )

            if (!canContinueBackfill) {
                log.i("Conversations backward backfill complete")
                return
            }
            before = page.page.backwardCursor
        }
    }

    private suspend fun fetchConversationsPage(cursor: String?, before: String?): GeneratedConversationSyncPage {
        val hubResponse = hubSyncCoordinator.getConversations(cursor = cursor, before = before)
        val response = hubResponse.response
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string() ?: "(no body)"
            log.e("fetchConversationsPage: HTTP ${response.code()} error: ${errorBody.take(200)}")
            throw IllegalStateException("Failed to sync conversations. HTTP ${response.code()}")
        }

        val responseBody = response.body()?.string()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Failed to sync conversations. Empty response body.")

        return try {
            val page = conversationsSyncPageAdapter.fromJson(responseBody)
                ?: throw IllegalStateException("Failed to sync conversations. Unparseable response body.")
            log.d("Parsed ${page.conversations.size} conversations from page, hasMore: ${page.hasMore}")
            GeneratedConversationSyncPage(
                generation = hubResponse.generation,
                page = page,
            )
        } catch (error: Exception) {
            log.e("fetchConversationsPage: parse failed: ${error.message}")
            throw error
        }
    }

    private suspend fun persistConversationsPage(
        page: ConversationSyncPage,
        generation: Long,
        historyComplete: Boolean,
        preserveForwardCursorWhenNull: Boolean,
        preserveBackwardCursorWhenNull: Boolean,
    ) {
        transactionRunner.withTransaction {
            hubSyncCoordinator.ensureCanPersist(generation)
            // Field-preserving merge: participants embedded in sync pages can carry null
            // name/avatar; never let them degrade good local rows.
            participantsDao.mergeParticipants(page.participants.map { it.toEntity() })
            conversationsDao.upsertConversations(page.conversations.map { it.toEntity() })

            val existingState = syncStateDao.getSyncState(CONVERSATIONS_SYNC_KEY)
            val resolvedForwardCursor = if (page.nextCursor == null && preserveForwardCursorWhenNull) {
                existingState?.forwardCursor
            } else {
                page.nextCursor
            }
            val resolvedBackwardCursor = if (page.backwardCursor == null && preserveBackwardCursorWhenNull) {
                existingState?.backwardCursor
            } else {
                page.backwardCursor
            }

            syncStateDao.upsertSyncState(
                SyncStateEntity(
                    roverEntity = CONVERSATIONS_SYNC_KEY,
                    forwardCursor = resolvedForwardCursor,
                    backwardCursor = resolvedBackwardCursor,
                    historyComplete = historyComplete,
                )
            )
            hubSyncCoordinator.ensureCanPersist(generation)
        }
    }

    private data class GeneratedConversationSyncPage(
        val generation: Long,
        val page: ConversationSyncPage,
    )
}
