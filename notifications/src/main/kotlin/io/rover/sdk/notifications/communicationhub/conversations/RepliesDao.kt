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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface RepliesDao {
    @Query("SELECT * FROM replies WHERE conversationID = :conversationId ORDER BY createdAt ASC")
    suspend fun getRepliesForConversation(conversationId: String): List<ReplyEntity>

    @Upsert
    suspend fun upsertReply(reply: ReplyEntity)

    @Upsert
    suspend fun upsertReplies(replies: List<ReplyEntity>)

    @Query("DELETE FROM replies WHERE id = :replyId")
    suspend fun deleteReplyById(replyId: String)

    @Query("SELECT * FROM replies WHERE externalID = :externalId LIMIT 1")
    suspend fun getReplyByExternalId(externalId: String): ReplyEntity?

    /**
     * Returns all pending (queued) replies oldest-first, regardless of [ReplyEntity.nextRetryAt].
     *
     * Per-attempt eligibility (whether a reply's backoff has elapsed) is deliberately decided in
     * the flush pass rather than filtered here, so an older reply still in backoff can *block* its
     * conversation's successors (head-of-line blocking) instead of being skipped.
     */
    @Query("""
        SELECT * FROM replies
        WHERE syncState = :syncState
        AND (:conversationId IS NULL OR conversationID = :conversationId)
        ORDER BY createdAt ASC
        LIMIT :limit
    """)
    suspend fun getPendingReplies(
        syncState: String = ReplyEntity.SYNC_STATE_QUEUED,
        conversationId: String? = null,
        limit: Int = 50,
    ): List<ReplyEntity>

    /**
     * Returns the distinct conversation IDs that currently have at least one queued reply. The flush
     * pass iterates these and pages each conversation's replies separately, so the per-conversation
     * page limit can never let one conversation starve another out of a pass (conversations are
     * independent).
     */
    @Query("""
        SELECT DISTINCT conversationID FROM replies
        WHERE syncState = :syncState
        ORDER BY conversationID ASC
    """)
    suspend fun getConversationIdsWithPendingReplies(
        syncState: String = ReplyEntity.SYNC_STATE_QUEUED,
    ): List<String>

    @Query("""
        UPDATE replies
        SET syncState = :syncState,
            retryCount = :retryCount,
            nextRetryAt = :nextRetryAt,
            lastSendError = :lastSendError
        WHERE externalID = :externalId
    """)
    suspend fun updateSendStateByExternalId(
        externalId: String,
        syncState: String,
        retryCount: Int,
        nextRetryAt: Long?,
        lastSendError: String?,
    )

    @Query("""
        UPDATE replies
        SET syncState = :syncState,
            retryCount = :retryCount,
            nextRetryAt = :nextRetryAt,
            lastSendError = :lastSendError
        WHERE id = :id
    """)
    suspend fun updateSendStateById(
        id: String,
        syncState: String,
        retryCount: Int,
        nextRetryAt: Long?,
        lastSendError: String?,
    )

    /**
     * Terminally fails every queued reply whose optimistic-insert time ([ReplyEntity.createdAt]) is
     * older than [deadlineBefore], without attempting a send. Used to enforce the bounded retry
     * window even when no send pass ran during the window.
     */
    @Query("""
        UPDATE replies
        SET syncState = :failedState,
            nextRetryAt = NULL,
            lastSendError = :error
        WHERE syncState = :queuedState
        AND createdAt < :deadlineBefore
    """)
    suspend fun failExpiredQueuedReplies(
        deadlineBefore: Long,
        error: String,
        queuedState: String = ReplyEntity.SYNC_STATE_QUEUED,
        failedState: String = ReplyEntity.SYNC_STATE_FAILED,
    )

    @Query("DELETE FROM replies")
    suspend fun deleteAllReplies()

    @Query("SELECT * FROM replies")
    fun getAllRepliesFlow(): Flow<List<ReplyEntity>>

    /**
     * Counts incoming (participant) replies that are newer than the conversation's
     * last-read timestamp. Null lastReadAt means the conversation has never been
     * read — all incoming replies count.
     */
    @Query("""
        SELECT COUNT(*) FROM replies
        JOIN conversations ON conversations.id = replies.conversationID
        WHERE replies.participantID IS NOT NULL
        AND (conversations.lastReadAt IS NULL OR replies.createdAt > conversations.lastReadAt)
    """)
    fun getUnreadReplyCountFlow(): Flow<Int>
}
