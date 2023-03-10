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

package io.rover.sdk.location.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.rover.sdk.core.data.sync.CursorState
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage

class LocationDatabase(
    context: Context,
    localStorage: LocalStorage
) : SQLiteOpenHelper(
    context,
    "rover-location",
    null,
    2
),
    CursorState {

    private val storage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override fun onCreate(db: SQLiteDatabase) {
        GeofencesSqlStorage.initSchema(db)
        BeaconsSqlStorage.initSchema(db)
    }

    override fun cursorForKey(key: String): String? {
        return storage[key]
    }

    override fun setCursorForKey(key: String, cursor: String) {
        storage[key] = cursor
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        log.v("Rover Location database schema has changed, dropping and creating the database tables and forcing resync.")
        clearCursors()

        GeofencesSqlStorage.dropSchema(db)
        BeaconsSqlStorage.dropSchema(db)

        GeofencesSqlStorage.initSchema(db)
        BeaconsSqlStorage.initSchema(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    private fun clearCursors() {
        storage.keys.forEach { storage[it] = null }
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "location-sync-cursors"
    }
}
