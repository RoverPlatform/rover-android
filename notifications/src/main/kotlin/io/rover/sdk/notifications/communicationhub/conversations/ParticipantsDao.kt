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
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ParticipantsDao {
    @Query("SELECT * FROM participants WHERE id = :participantId")
    suspend fun getParticipantById(participantId: String): ParticipantEntity?

    @Query("SELECT * FROM participants")
    suspend fun getAllParticipants(): List<ParticipantEntity>

    @Query("SELECT * FROM participants")
    fun getAllParticipantsFlow(): Flow<List<ParticipantEntity>>

    @Upsert
    suspend fun upsertParticipant(participant: ParticipantEntity)

    @Upsert
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    /**
     * Field-preserving merge write: participant data is monotonic — a write may add rows or
     * improve fields, but never degrade them. An incoming null [ParticipantEntity.name],
     * [ParticipantEntity.bio], or [ParticipantEntity.avatarUrl] leaves the existing value in
     * place; non-null incoming values win.
     *
     * The server can legitimately return degraded participant data (empty or partial
     * `/participants` responses, unresolved member profiles yielding null names), and a
     * full-row [upsertParticipants] would destroy good local data, causing avatars to flap
     * between image, initial placeholder, and "?" states. The tradeoff: a field cannot
     * legitimately return to null until a Hub reset clears the table. That is accepted.
     */
    @Transaction
    suspend fun mergeParticipants(participants: List<ParticipantEntity>) {
        participants.forEach { mergeParticipant(it) }
    }

    /**
     * Single-row variant of [mergeParticipants]; same monotonic contract.
     */
    @Transaction
    suspend fun mergeParticipant(incoming: ParticipantEntity) {
        val existing = getParticipantById(incoming.id)
        val merged = if (existing == null) incoming else incoming.copy(
            name = incoming.name ?: existing.name,
            bio = incoming.bio ?: existing.bio,
            avatarUrl = incoming.avatarUrl ?: existing.avatarUrl,
        )
        upsertParticipant(merged)
    }

    @Query("DELETE FROM participants")
    suspend fun deleteAllParticipants()
}
