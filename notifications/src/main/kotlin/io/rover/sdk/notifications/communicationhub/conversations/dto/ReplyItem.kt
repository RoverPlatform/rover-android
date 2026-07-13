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
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import java.time.Instant

@JsonClass(generateAdapter = true)
internal data class ReplyItem(
    val id: String,
    val conversationID: String,
    val senderType: String,
    val participantID: String?,
    val content: List<ReplyContentBlockItem>,
    val externalID: String?,
    val createdAt: String,
) {
    fun toEntity(): ReplyEntity {
        return ReplyEntity(
            id = id,
            conversationID = conversationID,
            senderType = senderType,
            participantID = participantID,
            externalID = externalID,
            createdAt = Instant.parse(createdAt).toEpochMilli(),
            content = content.map { it.toEntity() },
            syncState = ReplyEntity.SYNC_STATE_CONFIRMED,
        )
    }
}

@JsonClass(generateAdapter = true)
internal data class ReplyContentBlockItem(
    val type: String,
    val text: String? = null,
    val url: String? = null,
) {
    fun toEntity(): ReplyContentBlock = ReplyContentBlock(type = type, text = text, url = url)

    companion object {
        fun text(value: String): ReplyContentBlockItem {
            return ReplyContentBlockItem(type = ReplyContentBlock.TYPE_TEXT, text = value)
        }

        fun image(url: String): ReplyContentBlockItem {
            return ReplyContentBlockItem(type = ReplyContentBlock.TYPE_IMAGE, url = url)
        }
    }
}
