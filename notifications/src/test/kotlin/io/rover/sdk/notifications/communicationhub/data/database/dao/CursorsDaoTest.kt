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

package io.rover.sdk.notifications.communicationhub.data.database.dao

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.data.database.entities.CursorEntity
import io.rover.sdk.notifications.communicationhub.data.RoverEngageTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CursorsDaoTest : RoverEngageTestBase() {

    @Test
    fun cursorsInsertAndRetrieve() = runBlocking {
        val dao = database!!.cursorsDao()
        val cursor = CursorEntity("posts", "cursor-page-1")
        dao.insertCursor(cursor)

        val retrieved = dao.getCursor("posts")
        assertThat(retrieved, equalTo("cursor-page-1"))
    }

    @Test
    fun cursorsGetCursorReturnsNullForNonExistent() = runBlocking {
        val dao = database!!.cursorsDao()
        val retrieved = dao.getCursor("non-existent")
        assertThat(retrieved, equalTo(null))
    }

    @Test
    fun cursorsUpdateCursor() = runBlocking {
        val dao = database!!.cursorsDao()
        dao.updateCursor("posts", "cursor-page-1")
        assertThat(dao.getCursor("posts"), equalTo("cursor-page-1"))

        dao.updateCursor("posts", "cursor-page-2")
        assertThat(dao.getCursor("posts"), equalTo("cursor-page-2"))
    }

    @Test
    fun cursorsInsertReplacesOnConflict() = runBlocking {
        val dao = database!!.cursorsDao()
        val cursor1 = CursorEntity("posts", "cursor-1")
        val cursor2 = CursorEntity("posts", "cursor-2")

        dao.insertCursor(cursor1)
        assertThat(dao.getCursor("posts"), equalTo("cursor-1"))

        dao.insertCursor(cursor2)
        assertThat(dao.getCursor("posts"), equalTo("cursor-2"))

        // Should only have one cursor for "posts"
        val all = dao.getAllCursors()
        assertThat(all.count { it.roverEntity == "posts" }, equalTo(1))
    }

    @Test
    fun cursorsGetAllCursors() = runBlocking {
        val dao = database!!.cursorsDao()
        dao.insertCursor(CursorEntity("posts", "posts-cursor"))
        dao.insertCursor(CursorEntity("subscriptions", "subs-cursor"))
        dao.insertCursor(CursorEntity("articles", "articles-cursor"))

        val all = dao.getAllCursors()
        assertThat(all.size, equalTo(3))
        assertThat(all.map { it.roverEntity }.toSet(), equalTo(setOf("posts", "subscriptions", "articles")))
    }

    @Test
    fun cursorsHandlesNullCursorValue() = runBlocking {
        val dao = database!!.cursorsDao()
        dao.updateCursor("posts", null)

        val retrieved = dao.getCursor("posts")
        assertThat(retrieved, equalTo(null))
    }
}