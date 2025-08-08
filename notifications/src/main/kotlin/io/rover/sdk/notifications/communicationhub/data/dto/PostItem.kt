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

package io.rover.sdk.notifications.communicationhub.data.dto

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import java.util.Date

@JsonClass(generateAdapter = true)
data class PostItem(
    val id: String,
    val subject: String,
    var isRead: Boolean,
    val previewText: String,
    val receivedAt: Date,
    val url: String?,
    @Json(name = "coverImageURL") val coverImageURL: String?,
    @Json(name = "subscriptionID") val subscriptionID: String?
) {
    fun toEntity(existingPostIsRead: Boolean = false): PostEntity {
        return PostEntity(
            id = id,
            subject = subject,
            previewText = previewText,
            receivedAt = receivedAt.time,
            url = url,
            coverImageURL = coverImageURL,
            // If the incoming post is read, then we should mark it as read in the database, but
            // not revert it to unread.
            isRead = existingPostIsRead || isRead,
            subscriptionId = subscriptionID
        )
    }
}