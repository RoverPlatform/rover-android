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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SyncStateDaoTest : RoverEngageTestBase() {

    @Test
    fun syncStateUsesSeparateKeysForConversationsAndReplies() = runBlocking {
        val dao = database!!.syncStateDao()
        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = "forward-conversations",
                backwardCursor = "backward-conversations",
                historyComplete = false,
            )
        )
        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "replies:conversation-1",
                forwardCursor = "forward-replies",
                backwardCursor = "backward-replies",
                historyComplete = true,
            )
        )

        val conversationState = dao.getSyncState("conversations")
        val repliesState = dao.getSyncState("replies:conversation-1")

        assertThat(conversationState?.forwardCursor, equalTo("forward-conversations"))
        assertThat(repliesState?.forwardCursor, equalTo("forward-replies"))
        assertThat(conversationState?.backwardCursor, equalTo("backward-conversations"))
        assertThat(repliesState?.backwardCursor, equalTo("backward-replies"))
    }

    @Test
    fun syncStateHistoryCompleteDefaultsFalseAndPersistsWhenSet() = runBlocking {
        val dao = database!!.syncStateDao()

        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = null,
                backwardCursor = null,
            )
        )
        assertThat(dao.getSyncState("conversations")?.historyComplete, equalTo(false))

        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = null,
                backwardCursor = "older-cursor",
                historyComplete = true,
            )
        )
        assertThat(dao.getSyncState("conversations")?.historyComplete, equalTo(true))
    }

    @Test
    fun syncStateHistoryCompleteIsMonotonicOnceTrue() = runBlocking {
        val dao = database!!.syncStateDao()

        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = "next-1",
                backwardCursor = "older-1",
                historyComplete = true,
            )
        )

        dao.upsertSyncState(
            SyncStateEntity(
                roverEntity = "conversations",
                forwardCursor = "next-2",
                backwardCursor = "older-2",
                historyComplete = false,
            )
        )

        val syncState = dao.getSyncState("conversations")
        assertThat(syncState?.historyComplete, equalTo(true))
        assertThat(syncState?.forwardCursor, equalTo("next-2"))
        assertThat(syncState?.backwardCursor, equalTo("older-2"))
    }
}
