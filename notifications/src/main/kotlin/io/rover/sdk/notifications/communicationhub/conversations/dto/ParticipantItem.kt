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
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import java.time.Instant

@JsonClass(generateAdapter = true)
internal data class ParticipantItem(
    val id: String,
    val name: String?,
    val avatarURL: String?,
    val updatedAt: String,
    val bio: String? = null,
) {
    fun toEntity(): ParticipantEntity {
        return ParticipantEntity(
            id = id,
            name = name,
            avatarUrl = avatarURL,
            updatedAt = Instant.parse(updatedAt).toEpochMilli(),
            bio = bio,
        )
    }
}
