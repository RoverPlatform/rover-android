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

package io.rover.sdk.notifications.communicationhub.conversations.dto

import com.squareup.moshi.JsonClass
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import java.time.Instant

@JsonClass(generateAdapter = true)
internal data class ConversationItem(
    val id: String,
    val subject: String?,
    val lastReplyAt: String,
    val lastIncomingReplyAt: String?,
    val lastIncomingParticipantID: String?,
    val lastReadAt: String?,
    val lastReadReplyID: String?,
    val lastReplyPreview: String?,
    val createdAt: String,
    val participantIDs: List<String>?,
    val updatedAt: String,
) {
    fun toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            subject = subject,
            lastReplyAt = lastReplyAt.toEpochMillis(),
            lastIncomingReplyAt = lastIncomingReplyAt.toEpochMillisOrNull(),
            lastIncomingParticipantID = lastIncomingParticipantID,
            lastReadAt = lastReadAt.toEpochMillisOrNull(),
            lastReadReplyID = lastReadReplyID,
            lastReplyPreview = lastReplyPreview,
            createdAt = createdAt.toEpochMillis(),
            participantIDs = participantIDs,
            updatedAt = updatedAt.toEpochMillis(),
        )
    }
}

private fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()
private fun String?.toEpochMillisOrNull(): Long? = this?.toEpochMillis()
