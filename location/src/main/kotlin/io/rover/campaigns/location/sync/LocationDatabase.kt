package io.rover.campaigns.location.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.rover.campaigns.core.data.sync.CursorState
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.LocalStorage

class LocationDatabase(
    context: Context,
    localStorage: LocalStorage
) : SQLiteOpenHelper(
    context,
    "rover-location",
    null,
    2
), CursorState {

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
        log.v("Rover Campaigns Location database schema has changed, dropping and creating the database tables and forcing resync.")
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
