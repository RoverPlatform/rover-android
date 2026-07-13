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

package io.rover.sdk.notifications.communicationhub.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.rover.sdk.notifications.communicationhub.conversations.ReplyContentBlock
import java.util.Date

internal class RoverEngageTypeConverters {
    private val moshi: Moshi = Moshi.Builder().build()

    private val participantIdsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    private val replyBlocksAdapter = moshi.adapter<List<ReplyContentBlock>>(
        Types.newParameterizedType(List::class.java, ReplyContentBlock::class.java)
    )
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromParticipantIds(participantIds: List<String>?): String? {
        return participantIds?.let { participantIdsAdapter.toJson(it) }
    }

    @TypeConverter
    fun toParticipantIds(raw: String?): List<String>? {
        if (raw == null) return null
        if (raw.isBlank()) return emptyList()
        return participantIdsAdapter.fromJson(raw) ?: emptyList()
    }

    @TypeConverter
    fun fromReplyBlocks(blocks: List<ReplyContentBlock>): String {
        return replyBlocksAdapter.toJson(blocks)
    }

    @TypeConverter
    fun toReplyBlocks(raw: String?): List<ReplyContentBlock> {
        if (raw.isNullOrBlank()) return emptyList()
        return replyBlocksAdapter.fromJson(raw) ?: emptyList()
    }
}
