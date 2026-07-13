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

import androidx.room.withTransaction
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.conversations.dto.ConversationItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ParticipantItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyContentBlockItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.ReplyItem
import io.rover.sdk.notifications.communicationhub.conversations.dto.RepliesSyncPage
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.data.network.MarkConversationReadRequest
import io.rover.sdk.notifications.communicationhub.data.network.MarkConversationReadResponse
import io.rover.sdk.notifications.communicationhub.data.network.SendConversationReplyRequest
import io.rover.sdk.notifications.communicationhub.sync.HubResetObserver
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.sync.SyncStateDao
import io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class ConversationsRepository(
    private val hubSyncCoordinator: HubSyncCoordinator,
    private val conversationsDao: ConversationsDao,
    private val repliesDao: RepliesDao,
    private val participantsDao: ParticipantsDao,
    private val syncStateDao: SyncStateDao,
    private val transactionRunner: ConversationsTransactionRunner,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollingIntervalMs: Long = 5_000L,
    // Wall-clock source for retry scheduling. Injectable so timer/deadline behaviour can be
    // driven deterministically alongside a test dispatcher's virtual clock.
    private val now: () -> Long = { System.currentTimeMillis() },
) : ConversationsDataSource, ConversationPushRepository, HubResetObserver {
    constructor(
        engageApiService: EngageApiService,
        conversationsDao: ConversationsDao,
        repliesDao: RepliesDao,
        participantsDao: ParticipantsDao,
        syncStateDao: SyncStateDao,
        transactionRunner: ConversationsTransactionRunner,
        backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
        pollingIntervalMs: Long = 5_000L,
        now: () -> Long = { System.currentTimeMillis() },
    ) : this(
        hubSyncCoordinator = HubSyncCoordinator(engageApiService),
        conversationsDao = conversationsDao,
        repliesDao = repliesDao,
        participantsDao = participantsDao,
        syncStateDao = syncStateDao,
        transactionRunner = transactionRunner,
        backgroundDispatcher = backgroundDispatcher,
        pollingIntervalMs = pollingIntervalMs,
        now = now,
    )

    private val moshi = Moshi.Builder().build()
    private val repliesSyncPageAdapter = moshi.adapter(RepliesSyncPage::class.java)
    private val markConversationReadResponseAdapter = moshi.adapter(MarkConversationReadResponse::class.java)
    private val pollingScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val repliesPollingJobs = ConcurrentHashMap<String, Job>()
    private val conversationsBackfillRunning = AtomicBoolean(false)
    private val resetVersion = MutableStateFlow(0L)

    // Single-consumer actor that serializes and coalesces outbound reply sends. Every send
    // path (user send, retry flush, polling, sync) funnels through [requestFlush], so at most
    // one send pass runs at a time and concurrent triggers can never double-send a queued reply.
    private val flushScope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val flushRequests = Channel<CompletableDeferred<Boolean>>(Channel.UNLIMITED)

    // Test-only instrumentation: counts send passes so tests can verify concurrent triggers
    // coalesce. Has no effect on production behavior; see [flushPassCount].
    private val flushPassCounter = AtomicInteger(0)

    // At most one in-flight wake-up, armed to the soonest moment a queued reply becomes actionable
    // so retries fire near their nextRetryAt instead of waiting on the 15s poll or the next sync.
    // Only ever read/written from the single-consumer actor loop below, so it needs no locking.
    private var retryTimerJob: Job? = null

    /** Number of send passes the actor has run. Test seam for verifying coalescing. */
    internal val flushPassCount: Int get() = flushPassCounter.get()

    init {
        flushScope.launch {
            for (firstTicket in flushRequests) {
                // Coalesce every ticket already enqueued before this pass starts. A ticket
                // enqueued *during* the pass is left for the next iteration, whose SELECT runs
                // after that enqueue — so an awaiting caller is always released by a pass that
                // observed its request (and any reply row it just queued).
                val batch = mutableListOf(firstTicket)
                while (true) {
                    batch += (flushRequests.tryReceive().getOrNull() ?: break)
                }

                val outcome = runCatching { runFlushPass() }
                // Re-arm the wake-up from the post-pass state. Runs on the actor thread, so the
                // timer field is mutated single-threaded.
                runCatching { rescheduleRetryTimer() }
                batch.forEach { ticket ->
                    outcome.fold(
                        onSuccess = { ticket.complete(it) },
                        onFailure = { ticket.completeExceptionally(it) },
                    )
                }
            }
        }
    }

    /**
     * Triggers a coalesced flush of all eligible queued replies and suspends until a pass that
     * observed this request completes, returning whether every eligible reply sent. A reset
     * (HTTP 410) surfaces as the thrown [IllegalStateException] from the pass.
     *
     * This is the single entry point for outbound reply sends: user sends, retries, polling, and
     * app-level sync all funnel through here, so at most one pass runs at a time.
     */
    internal suspend fun requestFlush(): Boolean {
        val ticket = CompletableDeferred<Boolean>()
        flushRequests.send(ticket)
        return ticket.await()
    }

    suspend fun fakeSyncConversations(
        conversations: List<ConversationItem>,
        participants: List<ParticipantItem>,
        forwardCursor: String?,
        backwardCursor: String?,
        historyComplete: Boolean,
    ) {
        participantsDao.mergeParticipants(participants.map { it.toEntity() })
        conversationsDao.upsertConversations(conversations.map { it.toEntity() })
        syncStateDao.upsertSyncState(
            SyncStateEntity(
                roverEntity = CONVERSATIONS_SYNC_KEY,
                forwardCursor = forwardCursor,
                backwardCursor = backwardCursor,
                historyComplete = historyComplete,
            )
        )
    }

    suspend fun fakeSyncReplies(
        conversationId: String,
        replies: List<ReplyItem>,
        forwardCursor: String?,
        backwardCursor: String?,
    ) {
        require(replies.all { it.conversationID == conversationId }) {
            "All replies must match conversationId=$conversationId"
        }
        upsertReplies(replies)
        syncStateDao.upsertSyncState(
            SyncStateEntity(
                roverEntity = repliesSyncKey(conversationId),
                forwardCursor = forwardCursor,
                backwardCursor = backwardCursor,
            )
        )
    }

    override suspend fun saveConversationPushPayload(
        conversation: ConversationItem,
        reply: ReplyItem,
        participant: ParticipantItem,
    ) {
        require(reply.conversationID == conversation.id) {
            "Reply ${reply.id} does not belong to conversation ${conversation.id}"
        }
        require(reply.participantID == null || reply.participantID == participant.id) {
            "Reply ${reply.id} does not belong to participant ${participant.id}"
        }

        // Field-preserving merge: the push payload's participant can carry null name/avatar
        // (server-side profile lookup misses); never let it degrade a good local row.
        participantsDao.mergeParticipant(participant.toEntity())

        val existingConversation = conversationsDao.getConversationById(conversation.id)

        val mergedParticipantIds = (
            (existingConversation?.participantIDs ?: emptyList()) +
                (conversation.participantIDs ?: emptyList()) +
                participant.id
            )
            .filterNotNull()
            .distinct()

        val conversationEntity = conversation.toEntity()
        val replyCreatedAt = Instant.parse(reply.createdAt).toEpochMilli()

        val mergedLastIncomingReplyAt = listOfNotNull(
            existingConversation?.lastIncomingReplyAt,
            conversationEntity.lastIncomingReplyAt,
            replyCreatedAt,
        ).maxOrNull()

        val mergedConversationEntity = conversationEntity.copy(
            subject = conversation.subject?.ifBlank { existingConversation?.subject } ?: existingConversation?.subject,
            participantIDs = mergedParticipantIds,
            updatedAt = listOfNotNull(
                existingConversation?.updatedAt,
                conversationEntity.updatedAt,
                replyCreatedAt,
            ).maxOrNull() ?: conversationEntity.updatedAt,
            lastIncomingReplyAt = mergedLastIncomingReplyAt,
            lastReadAt = listOfNotNull(
                existingConversation?.lastReadAt,
                conversationEntity.lastReadAt,
            ).maxOrNull(),
        )

        conversationsDao.upsertConversation(mergedConversationEntity)

        upsertReplies(
            listOf(
                reply.copy(participantID = reply.participantID ?: participant.id)
            )
        )
    }

    override fun getConversationsFlow(): Flow<List<ConversationEntity>> {
        return conversationsDao.getAllConversationsFlow()
    }

    override fun getParticipantsFlow(): Flow<List<ParticipantEntity>> {
        return participantsDao.getAllParticipantsFlow()
    }

    override fun getRepliesFlow(): Flow<List<ReplyEntity>> {
        return repliesDao.getAllRepliesFlow()
    }

    override fun getResetVersionFlow(): StateFlow<Long> {
        return resetVersion.asStateFlow()
    }

    fun getUnreadConversationCountFlow(): Flow<Int> = conversationsDao.getUnreadConversationCountFlow()

    override fun getRepliesBackwardCursorFlow(conversationId: String): Flow<String?> {
        return syncStateDao.getBackwardCursorFlow(repliesSyncKey(conversationId))
    }

    override suspend fun hasConversation(conversationId: String): Boolean {
        return withContext(backgroundDispatcher) {
            conversationsDao.getConversationById(conversationId) != null
        }
    }

    override suspend fun bootstrapLatestReplies(conversationId: String) {
        withContext(backgroundDispatcher) {
            requestFlush()
            val page = fetchRepliesPage(
                conversationId = conversationId,
                cursor = null,
                before = null,
            )
            val canContinueBackfill = page.page.hasMore && page.page.backwardCursor != null
            persistRepliesPage(
                conversationId = conversationId,
                page = page.page,
                generation = page.generation,
                historyComplete = !canContinueBackfill,
                preserveForwardCursorWhenNull = false,
                preserveBackwardCursorWhenNull = false,
            )
        }
    }

    override fun startRepliesForwardPolling(conversationId: String) {
        repliesPollingJobs.remove(conversationId)?.cancel()

        val job = pollingScope.launch {
            try {
                requestFlush()
                syncRepliesForwardPage(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e("startRepliesForwardPolling: initial sync failed for $conversationId: ${e::class.simpleName}: ${e.message}")
            }
            while (isActive) {
                delay(pollingIntervalMs)
                try {
                    requestFlush()
                    syncRepliesForwardPage(conversationId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.e("startRepliesForwardPolling: sync failed for $conversationId: ${e::class.simpleName}: ${e.message}")
                }
            }
        }

        repliesPollingJobs[conversationId] = job
    }

    override fun stopRepliesForwardPolling(conversationId: String) {
        repliesPollingJobs.remove(conversationId)?.cancel()
    }

    internal fun hasRepliesPollingJob(conversationId: String): Boolean {
        return repliesPollingJobs.containsKey(conversationId)
    }

    internal suspend fun runRepliesForwardSyncIteration(conversationId: String) {
        syncRepliesForwardPage(conversationId)
    }

    override suspend fun loadOlderReplies(
        conversationId: String,
        beforeReplyId: String?,
        beforeCursor: String?,
    ) {
        withContext(backgroundDispatcher) {
            val resolvedBefore = beforeCursor
                ?: syncStateDao.getSyncState(repliesSyncKey(conversationId))?.backwardCursor
                ?: return@withContext

            val page = fetchRepliesPage(
                conversationId = conversationId,
                cursor = null,
                before = resolvedBefore,
            )
            val canContinueBackfill = page.page.hasMore && page.page.backwardCursor != null
            persistRepliesPage(
                conversationId = conversationId,
                page = page.page,
                generation = page.generation,
                historyComplete = !canContinueBackfill,
                preserveForwardCursorWhenNull = true,
                preserveBackwardCursorWhenNull = false,
            )
        }
    }

    override suspend fun markConversationRead(conversationId: String, lastReadReplyId: String?) {
        withContext(backgroundDispatcher) {
            val hubResponse = try {
                hubSyncCoordinator.markConversationRead(
                    conversationId = conversationId,
                    body = MarkConversationReadRequest(lastReadReplyId = lastReadReplyId),
                )
            } catch (resetRequired: SyncResetRequiredException) {
                handleResetRequired("Failed to mark conversation read. HTTP 410", resetRequired)
            }
            val response = hubResponse.response

            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to mark conversation read. HTTP ${response.code()}")
            }

            val responseJson = response.body()?.string()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Failed to mark conversation read. Empty response body.")
            val responseBody = try {
                markConversationReadResponseAdapter.fromJson(responseJson)
            } catch (error: JsonDataException) {
                throw IllegalStateException("Failed to mark conversation read. Unparseable response body.", error)
            }
                ?: throw IllegalStateException("Failed to mark conversation read. Unparseable response body.")

            transactionRunner.withTransaction {
                hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
                val conversation = conversationsDao.getConversationById(responseBody.conversationID) ?: return@withTransaction
                conversationsDao.upsertConversation(
                    conversation.copy(
                        lastReadAt = Instant.parse(responseBody.lastReadAt).toEpochMilli(),
                        lastReadReplyID = responseBody.lastReadReplyID,
                    )
                )
                hubSyncCoordinator.ensureCanPersist(hubResponse.generation)
            }
        }
    }

    override suspend fun sendReply(
        conversationId: String,
        message: String,
        externalId: String?,
    ): String {
        val resolvedExternalId = externalId ?: UUID.randomUUID().toString()
        val trimmedMessage = message.trim()
        require(trimmedMessage.isNotEmpty()) { "Message must not be blank." }

        log.i("Sending reply to conversation $conversationId (externalId: $resolvedExternalId)")
        repliesDao.upsertReply(
            ReplyEntity(
                id = resolvedExternalId,
                conversationID = conversationId,
                senderType = ReplyEntity.SENDER_TYPE_FAN,
                participantID = null,
                externalID = resolvedExternalId,
                createdAt = now(),
                content = listOf(ReplyContentBlock(
                    type = ReplyContentBlock.TYPE_TEXT,
                    text = trimmedMessage,
                    url = null,
                )),
                syncState = ReplyEntity.SYNC_STATE_QUEUED,
            )
        )

        // Route the send through the flush actor so it serializes with retry/polling/sync passes
        // and can never double-send. The optimistic reply is already persisted as queued; the
        // pass attempts it, then we read back its resolved state to report success or failure.
        requestFlush()

        val persisted = repliesDao.getReplyByExternalId(resolvedExternalId)
        when (persisted?.syncState) {
            // A missing row after the optimistic insert + flush means the send state can't be
            // observed — not that the reply is legitimately pending — so surface it as a failure.
            // The message mirrors the terminal-failure fallback below since it may be shown to the
            // user; the specific cause (row vanished) is only meaningful for debugging.
            null -> {
                log.e("Reply $resolvedExternalId was not found after flush; cannot observe send state.")
                throw IllegalStateException("Failed to send reply.")
            }
            // Only a terminal failure is surfaced to the caller. A reply that is still `queued`
            // is legitimately pending — it may be waiting behind an older reply in the same
            // conversation (head-of-line blocking) or in backoff — so treat it as accepted.
            ReplyEntity.SYNC_STATE_FAILED ->
                throw IllegalStateException(persisted.lastSendError ?: "Failed to send reply.")
            else -> return resolvedExternalId
        }
    }

    companion object {
        private const val CONVERSATIONS_SYNC_KEY = "conversations"

        // Outbound reply retry tuning. These must match the iOS implementation for cross-platform
        // parity (see SDK-153 and the companion iOS issue).
        private const val MAX_BACKOFF_EXPONENT = 5
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
        private const val MAX_TOTAL_RETRY_MILLIS = 120_000L

        fun repliesSyncKey(conversationId: String): String {
            return "replies:$conversationId"
        }
    }

    private suspend fun syncRepliesForwardPage(conversationId: String) {
        withContext(backgroundDispatcher) {
            val cursor = syncStateDao.getSyncState(repliesSyncKey(conversationId))?.forwardCursor
            log.d("Syncing replies forward for conversation $conversationId, cursor: $cursor")
            val page = fetchRepliesPage(
                conversationId = conversationId,
                cursor = cursor,
                before = null,
            )
            log.d("Fetched ${page.page.replies.size} replies for conversation $conversationId")
            persistRepliesPage(
                conversationId = conversationId,
                page = page.page,
                generation = page.generation,
                historyComplete = false,
                preserveForwardCursorWhenNull = false,
                preserveBackwardCursorWhenNull = true,
            )
        }
    }

    private suspend fun runFlushPass(limit: Int = 50): Boolean {
        flushPassCounter.incrementAndGet()
        return withContext(backgroundDispatcher) {
            val now = now()
            // Deadline sweep: fail any queued reply already past createdAt + 120s without
            // attempting it. This honours the 2-minute cap even when no flush happened to run
            // during the window (app backgrounded, thread closed, process restarted), and unblocks
            // a conversation whose head has expired so its successors can proceed.
            repliesDao.failExpiredQueuedReplies(
                deadlineBefore = now - MAX_TOTAL_RETRY_MILLIS,
                error = "Send timed out.",
            )
            var allSucceeded = true
            // Process each conversation independently, strictly oldest-first. We page each
            // conversation's queued replies on its own (rather than taking one global LIMIT page and
            // grouping it) so that a conversation with many queued replies — or whose head is stuck
            // in backoff — can never crowd another conversation's eligible replies out of the pass.
            // `getPendingReplies` returns createdAt-ascending, so iterating yields oldest-first.
            for (conversationId in repliesDao.getConversationIdsWithPendingReplies()) {
                if (!isActive) break
                val replies = repliesDao.getPendingReplies(conversationId = conversationId, limit = limit)
                for (reply in replies) {
                    val externalId = reply.externalID
                    if (externalId.isNullOrBlank()) {
                        // Missing externalID is unrecoverable; fail it terminally. A terminal
                        // failure does not block successors, so continue to the next reply.
                        log.e("Queued reply ${reply.id} has no externalID; marking failed.")
                        repliesDao.updateSendStateById(
                            id = reply.id,
                            syncState = ReplyEntity.SYNC_STATE_FAILED,
                            retryCount = reply.retryCount,
                            nextRetryAt = null,
                            lastSendError = "Queued reply is missing externalID.",
                        )
                        allSucceeded = false
                        continue
                    }

                    // Head-of-line blocking: if this (oldest unsent) reply is still in backoff,
                    // stop here and leave its successors queued rather than leapfrogging it.
                    val eligible = reply.nextRetryAt == null || reply.nextRetryAt <= now
                    if (!eligible) break

                    val error = sendReplyRequest(
                        ReplySendRequest(
                            conversationId = reply.conversationID,
                            content = reply.content.mapNotNull { it.toRequestItem() },
                            externalId = externalId,
                            currentRetryCount = reply.retryCount,
                            createdAt = reply.createdAt,
                        )
                    )
                    if (error != null) {
                        allSucceeded = false
                        // A retryable failure leaves the reply queued (with a future nextRetryAt);
                        // it must block its successors. A terminal failure flips it to `failed`,
                        // which no longer blocks, so let the next reply proceed.
                        val updated = repliesDao.getReplyByExternalId(externalId)
                        if (updated?.syncState == ReplyEntity.SYNC_STATE_QUEUED) break
                    }
                }
            }
            allSucceeded
        }
    }

    /**
     * Arms a single timer to fire [requestFlush] at the soonest moment a queued reply becomes
     * actionable, replacing any previously-armed timer. Called from the actor loop after each pass,
     * so the field is only ever touched single-threaded.
     *
     * The wake-up is computed head-of-line-aware: only the oldest queued reply in each conversation
     * is actionable, so a not-yet-due successor sitting behind an in-backoff head must not pull the
     * wake time to "now" (which would busy-loop). When the soonest head is already due (e.g. a reply
     * deferred by the batch limit), we fire immediately to drain the remainder.
     */
    private suspend fun rescheduleRetryTimer() {
        retryTimerJob?.cancel()
        val wakeAt = soonestWakeAt()
        if (wakeAt == null) {
            retryTimerJob = null
            return
        }
        val delayMs = (wakeAt - now()).coerceAtLeast(0L)
        retryTimerJob = flushScope.launch {
            delay(delayMs)
            // A separate coroutine: it enqueues a flush ticket and the actor picks it up on its
            // next iteration. It never touches retryTimerJob, so there is no re-entrancy or deadlock.
            requestFlush()
        }
    }

    /**
     * The earliest time any conversation's head (oldest queued) reply is eligible to send, or null
     * if nothing is queued. A null head [ReplyEntity.nextRetryAt] means "due now" (0L).
     */
    private suspend fun soonestWakeAt(): Long? =
        repliesDao.getConversationIdsWithPendingReplies()
            .mapNotNull { repliesDao.getPendingReplies(conversationId = it, limit = 1).firstOrNull() }
            .map { it.nextRetryAt ?: 0L }
            .minOrNull()

    private suspend fun sendReplyRequest(request: ReplySendRequest): Throwable? {
        val hubResponse = try {
            hubSyncCoordinator.sendConversationReply(
                conversationId = request.conversationId,
                body = SendConversationReplyRequest(
                    content = request.content,
                    externalID = request.externalId,
                )
            )
        } catch (resetRequired: SyncResetRequiredException) {
            handleResetRequired("Failed to send reply. HTTP 410", resetRequired)
        } catch (e: CancellationException) {
            throw e
        } catch (exception: Exception) {
            markReplySendFailure(
                externalId = request.externalId,
                createdAt = request.createdAt,
                currentRetryCount = request.currentRetryCount,
                throwable = exception,
                retryable = isRetryableSendException(exception),
            )
            log.e("sendReply: failed for conversation ${request.conversationId}: ${exception.message}")
            return exception
        }

        val response = hubResponse.response
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            val error = IllegalStateException("Failed to send reply. HTTP ${response.code()}")
            markReplySendFailure(
                externalId = request.externalId,
                createdAt = request.createdAt,
                currentRetryCount = request.currentRetryCount,
                throwable = error,
                retryable = isRetryableSendStatus(response.code()),
            )
            log.e("sendReply: HTTP ${response.code()} error for conversation ${request.conversationId}")
            return error
        }

        response.body()?.close()
        repliesDao.updateSendStateByExternalId(
            externalId = request.externalId,
            syncState = ReplyEntity.SYNC_STATE_SENT,
            retryCount = 0,
            nextRetryAt = null,
            lastSendError = null,
        )
        log.i("Reply sent successfully for conversation ${request.conversationId}")
        return null
    }

    private suspend fun markReplySendFailure(
        externalId: String,
        createdAt: Long,
        currentRetryCount: Int,
        throwable: Throwable,
        retryable: Boolean,
    ) {
        val nextRetryCount = currentRetryCount + 1
        val nextRetryAt = now() + backoffDelayMillis(nextRetryCount)
        // Anchor the retry window to the reply's optimistic-insert time so the cap reflects
        // wall-clock time as the user perceives it, independent of retry cadence. If the next
        // scheduled retry would land past the deadline, give up and fail terminally now.
        val deadlineExceeded = nextRetryAt > createdAt + MAX_TOTAL_RETRY_MILLIS
        val terminal = !retryable || deadlineExceeded
        repliesDao.updateSendStateByExternalId(
            externalId = externalId,
            syncState = if (terminal) ReplyEntity.SYNC_STATE_FAILED else ReplyEntity.SYNC_STATE_QUEUED,
            retryCount = nextRetryCount,
            nextRetryAt = if (terminal) null else nextRetryAt,
            lastSendError = throwable.message,
        )
    }

    private fun isRetryableSendStatus(statusCode: Int): Boolean {
        return statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }

    private fun isRetryableSendException(throwable: Throwable): Boolean {
        return throwable is IOException
    }

    private fun backoffDelayMillis(retryCount: Int): Long {
        val clampedExponent = retryCount.coerceIn(1, MAX_BACKOFF_EXPONENT)
        return ((1L shl clampedExponent) * 1_000L).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }

    private suspend fun fetchRepliesPage(conversationId: String, cursor: String?, before: String?): GeneratedRepliesSyncPage {
        val hubResponse = try {
            hubSyncCoordinator.getConversationReplies(
                conversationId = conversationId,
                cursor = cursor,
                before = before,
            )
        } catch (resetRequired: SyncResetRequiredException) {
            handleResetRequired("Failed to sync replies. HTTP 410", resetRequired)
        }
        val response = hubResponse.response
        if (!response.isSuccessful) {
            log.e("fetchRepliesPage: HTTP ${response.code()} error for conversation $conversationId")
            throw IllegalStateException("Failed to sync replies. HTTP ${response.code()}")
        }

        val responseBody = response.body()?.string()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Failed to sync replies. Empty response body.")

        val page = repliesSyncPageAdapter.fromJson(responseBody)
            ?: throw IllegalStateException("Failed to sync replies. Unparseable response body.")
        log.d("Parsed ${page.replies.size} replies from page for conversation $conversationId, hasMore: ${page.hasMore}")
        return GeneratedRepliesSyncPage(
            generation = hubResponse.generation,
            page = page,
        )
    }

    private suspend fun persistRepliesPage(
        conversationId: String,
        page: RepliesSyncPage,
        generation: Long,
        historyComplete: Boolean,
        preserveForwardCursorWhenNull: Boolean,
        preserveBackwardCursorWhenNull: Boolean,
    ) {
        transactionRunner.withTransaction {
            hubSyncCoordinator.ensureCanPersist(generation)
            ensureParticipantsExist(page.replies.mapNotNull { it.participantID })

            val replies = page.replies
                .filter { it.conversationID == conversationId }
                .map { reply ->
                    ReplyEntity(
                        id = reply.id,
                        conversationID = reply.conversationID,
                        senderType = reply.senderType,
                        participantID = reply.participantID,
                        externalID = reply.externalID,
                        createdAt = Instant.parse(reply.createdAt).toEpochMilli(),
                        content = reply.content.map(ReplyContentBlockItem::toEntity),
                        syncState = ReplyEntity.SYNC_STATE_CONFIRMED,
                    )
                }

            ensureConversationRowsForReplies(conversationId, replies)

            upsertReplyEntities(page.replies, replies)
            val syncKey = repliesSyncKey(conversationId)
            val existingState = syncStateDao.getSyncState(syncKey)
            val resolvedForwardCursor = if (page.nextCursor == null && preserveForwardCursorWhenNull) {
                existingState?.forwardCursor
            } else {
                page.nextCursor
            }
            val resolvedBackwardCursor = when {
                historyComplete -> null
                page.backwardCursor == null && preserveBackwardCursorWhenNull -> existingState?.backwardCursor
                else -> page.backwardCursor
            }

            syncStateDao.upsertSyncState(
                SyncStateEntity(
                    roverEntity = syncKey,
                    forwardCursor = resolvedForwardCursor,
                    backwardCursor = resolvedBackwardCursor,
                    historyComplete = historyComplete,
                )
            )
            hubSyncCoordinator.ensureCanPersist(generation)
        }
    }

    private suspend fun ensureConversationRowsForReplies(conversationId: String, replies: List<ReplyEntity>) {
        if (replies.isEmpty()) return
        if (conversationsDao.getConversationById(conversationId) != null) return

        val updatedAt = replies.maxOfOrNull { it.createdAt } ?: return
        conversationsDao.upsertConversation(
            ConversationEntity(
                id = conversationId,
                subject = "",
                lastReplyAt = updatedAt,
                lastIncomingReplyAt = null,
                lastIncomingParticipantID = null,
                lastReadAt = null,
                lastReadReplyID = null,
                lastReplyPreview = null,
                createdAt = updatedAt,
                participantIDs = emptyList(),
                updatedAt = updatedAt,
            )
        )
    }

    private suspend fun ensureParticipantsExist(participantIds: List<String>) {
        val missingParticipants = participantIds
            .distinct()
            .filter { participantsDao.getParticipantById(it) == null }
            .map { participantId ->
                ParticipantEntity(
                    id = participantId,
                    name = null,
                    avatarUrl = null,
                    updatedAt = 0L,
                )
            }

        if (missingParticipants.isNotEmpty()) {
            participantsDao.upsertParticipants(missingParticipants)
        }
    }

    private suspend fun upsertReplies(replies: List<ReplyItem>) {
        replies.forEach { reply ->
            reply.externalID?.let { externalId ->
                if (externalId != reply.id) {
                    repliesDao.deleteReplyById(externalId)
                }
            }
        }

        repliesDao.upsertReplies(replies.map { it.toEntity() })
    }

    private suspend fun upsertReplyEntities(replyItems: List<ReplyItem>, replies: List<ReplyEntity>) {
        replies.forEach { replyEntity ->
            replyItems.firstOrNull { it.id == replyEntity.id }?.externalID?.let { externalId ->
                if (externalId != replyEntity.id) {
                    repliesDao.deleteReplyById(externalId)
                }
            }
        }

        repliesDao.upsertReplies(replies)
    }

    internal suspend fun clearConversationData() {
        transactionRunner.withTransaction {
            repliesDao.deleteAllReplies()
            participantsDao.deleteAllParticipants()
            conversationsDao.deleteAllConversations()
            syncStateDao.deleteConversationSyncStates()
        }
    }

    internal suspend fun runConversationHistoryBackfillLock(block: suspend () -> Unit) {
        if (!conversationsBackfillRunning.compareAndSet(false, true)) {
            log.d("triggerEagerBackwardHistoryBackfill: already running, skipping")
            return
        }

        try {
            block()
        } finally {
            conversationsBackfillRunning.set(false)
        }
    }

    internal fun cancelRepliesPollingJobs() {
        repliesPollingJobs.values.forEach { it.cancel() }
        repliesPollingJobs.clear()
    }

    /**
     * Tears down the polling jobs and the flush actor. Production never calls this — the
     * repository is a process-lifetime singleton — but tests use it to avoid leaking the
     * always-on actor coroutine across cases.
     */
    internal fun close() {
        cancelRepliesPollingJobs()
        flushRequests.close()
        flushScope.cancel()
    }

    internal fun signalConversationReset() {
        resetVersion.update { it + 1 }
    }

    override suspend fun onHubReset() {
        cancelRepliesPollingJobs()
        signalConversationReset()
    }

    private suspend fun handleResetRequired(message: String, resetRequired: SyncResetRequiredException): Nothing {
        hubSyncCoordinator.resetAfterSyncCancellation()
        if (!hubSyncCoordinator.resetsDatabase) {
            clearConversationData()
            onHubReset()
        }
        throw IllegalStateException(message, resetRequired)
    }

    private data class GeneratedRepliesSyncPage(
        val generation: Long,
        val page: RepliesSyncPage,
    )

    private data class ReplySendRequest(
        val conversationId: String,
        val content: List<ReplyContentBlockItem>,
        val externalId: String,
        val currentRetryCount: Int,
        val createdAt: Long,
    )
}

private fun ReplyContentBlock.toRequestItem(): ReplyContentBlockItem? {
    return when (type) {
        ReplyContentBlock.TYPE_TEXT -> text?.let(ReplyContentBlockItem::text)
        ReplyContentBlock.TYPE_IMAGE -> url?.let(ReplyContentBlockItem::image)
        else -> null
    }
}

internal interface ConversationsTransactionRunner {
    suspend fun <T> withTransaction(block: suspend () -> T): T
}

internal class RoomConversationsTransactionRunner(
    private val database: androidx.room.RoomDatabase,
) : ConversationsTransactionRunner {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }
}
