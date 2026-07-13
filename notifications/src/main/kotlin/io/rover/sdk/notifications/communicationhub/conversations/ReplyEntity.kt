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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "replies",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationID"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantID"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["conversationID"]),
        Index(value = ["participantID"]),
        // externalID is the stable idempotency key for outgoing (fan) replies; enforce uniqueness
        // at the DB level. SQLite treats NULLs as distinct in a unique index, so incoming
        // (participant) replies — which carry a null externalID — are unaffected.
        Index(value = ["externalID"], unique = true),
    ]
)
internal data class ReplyEntity(
    @PrimaryKey val id: String,
    val conversationID: String,
    val senderType: String,
    val participantID: String?,
    val externalID: String?,
    val createdAt: Long,
    val content: List<ReplyContentBlock>,
    @ColumnInfo(defaultValue = "'confirmed'")
    val syncState: String = SYNC_STATE_CONFIRMED,
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
    val nextRetryAt: Long? = null,
    val lastSendError: String? = null,
) {
    companion object {
        const val SENDER_TYPE_FAN = "fan"
        const val SENDER_TYPE_PARTICIPANT = "participant"
        const val SYNC_STATE_QUEUED = "queued"
        const val SYNC_STATE_SENT = "sent"
        const val SYNC_STATE_CONFIRMED = "confirmed"
        const val SYNC_STATE_FAILED = "failed"
    }
}

@JsonClass(generateAdapter = true)
internal data class ReplyContentBlock(
    val type: String,
    val text: String?,
    val url: String?,
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
    }
}
