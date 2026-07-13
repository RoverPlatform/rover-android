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

package io.rover.sdk.notifications.communicationhub.sync

import io.rover.sdk.core.data.sync.SyncResetHandler
import io.rover.sdk.core.data.sync.SyncResetRequiredException
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.DeliveredHubNotificationClearer
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDao
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsTransactionRunner
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantsDao
import io.rover.sdk.notifications.communicationhub.conversations.RepliesDao
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.data.network.MarkConversationReadRequest
import io.rover.sdk.notifications.communicationhub.data.network.SendConversationReplyRequest
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.posts.PostsDao
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionsDao
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Response envelope for Hub/Engage API calls.
 *
 * [generation] is captured immediately before the network request starts. Callers must pass it
 * back to [HubSyncCoordinator.ensureCanPersist] before writing the response to Room. If a 410 reset
 * completed while the request was in flight, the generation will no longer match and the stale
 * write is skipped instead of repopulating the freshly-cleared database.
 */
internal data class HubSyncResponse<T>(
    val generation: Long,
    val response: Response<T>,
)

/**
 * Expected control-flow when a response was valid when requested but became stale before save.
 */
internal class StaleHubSyncGenerationException : RuntimeException("Hub sync generation changed before data could be persisted.")

/**
 * Observer for reset side effects that are not direct database deletion.
 *
 * The coordinator owns the data reset; observers own live runtime state such as polling jobs and
 * UI reset signals.
 */
internal interface HubResetObserver {
    suspend fun onHubReset()
}

/**
 * Owner of work that should be stopped before Hub reset clears local data.
 *
 * Regular standalone sync participants are cancelled by structured concurrency when the reset
 * originates from app-level sync. They also register here so a reset originating from outside
 * SyncCoordinator, such as a conversation action, can cancel their active pagination jobs before
 * Room is cleared. UI-triggered work, such as conversation history backfill, uses the same hook
 * because it is never owned by SyncCoordinator.
 *
 * Implementations must wait until their tracked work has unwound before returning. Reset uses that
 * guarantee to avoid clearing Room while old-generation jobs are still running with stale cursors.
 */
internal interface HubResetCancellable {
    suspend fun cancelAndJoinHubResetInvalidatedWork()
}

/**
 * Tracks active coroutine jobs that should be cancelled and joined when Hub data is invalidated.
 */
internal class HubResetJobTracker(
    private val cancellationMessage: String,
) : HubResetCancellable {
    private val lock = Any()
    private val activeJobs = mutableSetOf<Job>()

    suspend fun <T> track(block: suspend () -> T): T {
        val job = currentCoroutineContext().job
        synchronized(lock) {
            activeJobs.add(job)
        }

        try {
            return block()
        } finally {
            synchronized(lock) {
                activeJobs.remove(job)
            }
        }
    }

    suspend fun untrackCurrentJob() {
        val job = currentCoroutineContext().job
        synchronized(lock) {
            activeJobs.remove(job)
        }
    }

    override suspend fun cancelAndJoinHubResetInvalidatedWork() {
        val jobs = synchronized(lock) {
            activeJobs.toList()
        }
        jobs.forEach { job ->
            job.cancel(CancellationException(cancellationMessage))
        }
        jobs.forEach { job ->
            job.join()
        }
    }
}

/**
 * Central control point for Communication Hub's Engage API calls and server-driven reset flow.
 *
 * All Hub/Engage network paths that can receive HTTP 410 should go through this coordinator rather
 * than calling [EngageApiService] directly. That gives every caller the same behavior:
 *
 * 1. Capture the current reset generation before the HTTP call.
 * 2. Convert current-generation HTTP 410 into [SyncResetRequiredException].
 * 3. During app-level sync, let `SyncCoordinator` cancel sibling standalone sync participants first.
 * 4. Run [resetAfterSyncCancellation] after that cancellation has unwound.
 * 5. Require callers to generation-check before persisting the response.
 *
 * This separation is important because the 410 is commonly observed from inside a sync coroutine.
 * Dropping Room data immediately from that coroutine would race with other standalone participants
 * that are syncing in parallel. Instead, the exception crosses back to `SyncCoordinator` for
 * app-level sync, which cancels the rest of the current sync and then invokes this coordinator as
 * the reset handler. Non-sync callers still use this same coordinator, but they run the reset after
 * their own 410-producing operation because they are not inside `SyncCoordinator`'s cancellation
 * scope; generation checks prevent stale in-flight sync responses from writing after that reset.
 *
 * [resetsDatabase] is false only for legacy tests that construct this coordinator with just a mock
 * [EngageApiService]. Production assembly provides all DAO dependencies, so the reset path clears
 * the full Engage database, not just conversation tables.
 */
internal class HubSyncCoordinator(
    private val engageApiService: EngageApiService,
    private val postsDao: PostsDao? = null,
    private val subscriptionsDao: SubscriptionsDao? = null,
    private val conversationsDao: ConversationsDao? = null,
    private val repliesDao: RepliesDao? = null,
    private val participantsDao: ParticipantsDao? = null,
    private val syncStateDao: SyncStateDao? = null,
    private val transactionRunner: ConversationsTransactionRunner? = null,
    private val hubCoordinator: HubCoordinator = HubCoordinator(),
    private val deliveredHubNotificationClearer: DeliveredHubNotificationClearer? = null,
) : SyncResetHandler {
    private val generation = AtomicLong(0L)
    private val resetMutex = Mutex()
    private val lock = Any()
    private val resetObservers = mutableSetOf<HubResetObserver>()
    private val resetCancellables = mutableSetOf<HubResetCancellable>()

    /**
     * True after a current-generation 410 has requested Hub invalidation.
     *
     * While this is true, duplicate 410s from the same generation coalesce onto the same reset
     * instead of starting additional destructive clears. Access is guarded by [lock].
     */
    private var isInvalidating = false

    /**
     * Generation that first observed the active invalidation.
     *
     * Successful responses only clear [isInvalidating] if they started after this generation, which
     * prevents old pre-reset successes from reopening the 410 gate for other stale responses.
     * Access is guarded by [lock].
     */
    private var invalidatingGeneration = 0L

    /**
     * True once [resetAfterSyncCancellation] has completed the reset for the active invalidation.
     *
     * This lets duplicate reset-handler calls become no-ops, while still allowing a later 410 from
     * the current generation to start a new invalidation. Access is guarded by [lock].
     */
    private var resetCompleted = false

    val resetsDatabase: Boolean
        get() = transactionRunner != null &&
            repliesDao != null &&
            conversationsDao != null &&
            participantsDao != null &&
            postsDao != null &&
            subscriptionsDao != null &&
            syncStateDao != null

    /**
     * Registers non-database reset work.
     *
     * In production this is used by [io.rover.sdk.notifications.communicationhub.conversations.ConversationsRepository]
     * to cancel reply polling jobs and advance the reset version observed by conversation detail UI.
     */
    fun registerResetObserver(observer: HubResetObserver) {
        synchronized(lock) {
            resetObservers.add(observer)
        }
    }

    /**
     * Registers out-of-band work that should be cancelled when Hub data is invalidated.
     *
     * This should not be needed for ordinary standalone sync participants, because
     * `SyncCoordinator` already cancels them as sibling coroutines before invoking reset.
     */
    fun registerResetCancellable(cancellable: HubResetCancellable) {
        synchronized(lock) {
            resetCancellables.add(cancellable)
        }
    }

    fun currentGeneration(): Long = generation.get()

    /**
     * Verifies that a caller can still persist a response captured at [expectedGeneration].
     *
     * Call this immediately before and, for multi-step writes, immediately after Room writes inside
     * the same transaction. The first check prevents known-stale writes from starting; the second
     * catches a reset that completed while the transaction was suspended.
     */
    suspend fun ensureCanPersist(expectedGeneration: Long) {
        currentCoroutineContext().ensureActive()
        if (generation.get() != expectedGeneration) {
            throw StaleHubSyncGenerationException()
        }
    }

    suspend fun getPosts(deviceIdentifier: String, cursor: String?): HubSyncResponse<ResponseBody> =
        call { engageApiService.getPosts(deviceIdentifier, cursor) }

    suspend fun getSubscriptions(): HubSyncResponse<ResponseBody> =
        call { engageApiService.getSubscriptions() }

    suspend fun getParticipants(): HubSyncResponse<ResponseBody> =
        call { engageApiService.getParticipants() }

    suspend fun getConversations(cursor: String?, before: String?): HubSyncResponse<ResponseBody> =
        call { engageApiService.getConversations(cursor = cursor, before = before) }

    suspend fun getConversationReplies(
        conversationId: String,
        cursor: String?,
        before: String?,
    ): HubSyncResponse<ResponseBody> =
        call { engageApiService.getConversationReplies(conversationId, cursor, before) }

    suspend fun sendConversationReply(
        conversationId: String,
        body: SendConversationReplyRequest,
    ): HubSyncResponse<ResponseBody> =
        call { engageApiService.sendConversationReply(conversationId, body) }

    suspend fun markConversationRead(
        conversationId: String,
        body: MarkConversationReadRequest,
    ): HubSyncResponse<ResponseBody> =
        call { engageApiService.markConversationRead(conversationId, body) }

    /**
     * Performs the destructive reset after active sync work has been cancelled.
     *
     * This method is intentionally the [SyncResetHandler] entry point rather than being called
     * directly from [call]. When HTTP 410 happens during app-level sync, [call] throws
     * [SyncResetRequiredException]; `SyncCoordinator` cancels the sibling standalone sync jobs and
     * only then invokes this method. That ordering makes Room quiescent before the delete
     * transaction runs.
     *
     * The body runs in [NonCancellable] because the reset itself must complete even if the coroutine
     * that originally observed the 410 was cancelled as part of the sync abort.
     */
    override suspend fun resetAfterSyncCancellation() {
        resetMutex.withLock {
            val (cancellables, observers) = synchronized(lock) {
                if (!isInvalidating || resetCompleted) return
                resetCancellables.toList() to resetObservers.toList()
            }

            withContext(NonCancellable) {
                val newGeneration = generation.incrementAndGet()
                log.i("Resetting Hub data after server-driven reset. New generation: $newGeneration")

                cancellables.forEach { cancellable ->
                    try {
                        cancellable.cancelAndJoinHubResetInvalidatedWork()
                    } catch (error: Exception) {
                        log.w("Hub reset cancellation hook failed: ${error.message}")
                    }
                }

                if (resetsDatabase) {
                    transactionRunner!!.withTransaction {
                        repliesDao!!.deleteAllReplies()
                        conversationsDao!!.deleteAllConversations()
                        participantsDao!!.deleteAllParticipants()
                        postsDao!!.deleteAllPosts()
                        subscriptionsDao!!.clearAllSubscriptions()
                        syncStateDao!!.deleteAllSyncStates()
                    }
                } else {
                    log.w("Hub reset requested without database dependencies; only generation and observers will be updated.")
                }

                // Cancel any Hub push notifications still sitting in the tray. They predate the
                // identity change that triggered the 410 and now point at content that was just
                // dropped; selection is by payload marker, independent of the (now-cleared) Room
                // rows, so both posts and conversations are covered. Runs regardless of
                // [resetsDatabase] since it does not depend on the DAOs.
                try {
                    deliveredHubNotificationClearer?.clearDeliveredHubNotifications()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.w("Failed to clear delivered Hub notifications on reset: ${error.message}")
                }

                observers.forEach { observer ->
                    try {
                        observer.onHubReset()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        log.w("Hub reset observer failed: ${error.message}")
                    }
                }
                hubCoordinator.navigateToHome()
            }

            synchronized(lock) {
                resetCompleted = true
            }
        }
    }

    /**
     * Executes a Hub API call with 410 detection and generation capture.
     *
     * A successful response after a reset clears the invalidation gate only if the request started
     * after the generation that initiated invalidation. Older in-flight successes cannot re-enable
     * the gate because they may still represent the pre-reset user/session.
     */
    private suspend fun <T> call(httpCall: suspend () -> Response<T>): HubSyncResponse<T> {
        val requestGeneration = generation.get()
        val response = httpCall()

        if (response.code() == 410) {
            val staleResetResponse = synchronized(lock) {
                if (requestGeneration != generation.get()) {
                    true
                } else {
                    if (!isInvalidating || resetCompleted) {
                        isInvalidating = true
                        resetCompleted = false
                        invalidatingGeneration = requestGeneration
                    }
                    false
                }
            }
            if (staleResetResponse) {
                log.d("Ignoring stale Hub 410 from generation $requestGeneration; current generation is ${generation.get()}")
                return HubSyncResponse(
                    generation = requestGeneration,
                    response = response,
                )
            }
            throw SyncResetRequiredException(this)
        }

        if (response.isSuccessful) {
            synchronized(lock) {
                if (isInvalidating && requestGeneration > invalidatingGeneration) {
                    isInvalidating = false
                    resetCompleted = false
                }
            }
        }

        return HubSyncResponse(
            generation = requestGeneration,
            response = response,
        )
    }
}
