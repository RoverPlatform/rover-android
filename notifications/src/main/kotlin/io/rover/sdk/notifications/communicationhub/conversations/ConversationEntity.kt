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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
internal data class ConversationEntity(
    @PrimaryKey val id: String,
    val subject: String?,
    val lastReplyAt: Long,
    val lastIncomingReplyAt: Long?,
    val lastIncomingParticipantID: String?,
    val lastReadAt: Long?,
    val lastReadReplyID: String?,
    val lastReplyPreview: String?,
    val createdAt: Long,
    val participantIDs: List<String>?,
    val updatedAt: Long,
)

/**
 * Whether the conversation has activity the user has not read yet, preferring the incoming-reply
 * timestamp so the user's own outgoing replies never mark a conversation unread.
 */
internal val ConversationEntity.isUnread: Boolean
    get() {
        val unreadTimestamp = lastIncomingReplyAt ?: lastReplyAt
        return lastReadAt == null || unreadTimestamp > lastReadAt
    }

/**
 * The newest conversation activity that can contribute to the inbox badge.
 *
 * `lastReplyAt` includes the fan's own replies, so using it here would badge an outbound send.
 * `lastIncomingReplyAt` is the direction-specific signal maintained by conversation sync and the
 * push merge. A missing value cannot safely be inferred as incoming and therefore does not badge.
 */
internal val ConversationEntity.badgeActivityAt: Long?
    get() = lastIncomingReplyAt
