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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_states WHERE roverEntity = :roverEntity")
    suspend fun getSyncState(roverEntity: String): SyncStateEntity?

    @Query("SELECT backwardCursor FROM sync_states WHERE roverEntity = :roverEntity")
    fun getBackwardCursorFlow(roverEntity: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSyncState(syncState: SyncStateEntity)

    @Query("DELETE FROM sync_states WHERE roverEntity = 'conversations' OR roverEntity LIKE 'replies:%'")
    suspend fun deleteConversationSyncStates()

    @Query("DELETE FROM sync_states")
    suspend fun deleteAllSyncStates()

    @Transaction
    suspend fun upsertSyncState(syncState: SyncStateEntity) {
        val existingHistoryComplete = getSyncState(syncState.roverEntity)?.historyComplete ?: false
        replaceSyncState(
            syncState.copy(historyComplete = existingHistoryComplete || syncState.historyComplete)
        )
    }
}
