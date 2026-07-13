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

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.rover.sdk.notifications.communicationhub.data.RoverEngageDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoverEngageDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "rover_engage_database"

    @After
    fun teardown() {
        RoverEngageDatabase.getDatabase(context).close()
        val instanceField = RoverEngageDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
        context.deleteDatabase(dbName)
    }

    @Test
    fun databaseUpgradeFromV1PreservesLegacyInboxDataAndAddsConversationsSchema() = runBlocking {
        createVersion1DatabaseWithLegacyData()

        val migratedDatabase = RoverEngageDatabase.getDatabase(context)

        val post = migratedDatabase.postsDao().getPostById("post-1")
        val subscription = migratedDatabase.subscriptionsDao().getSubscriptionById("sub-1")
        val postsForwardCursor = migratedDatabase.syncStateDao().getSyncState("posts")?.forwardCursor

        assertThat(post?.id, equalTo("post-1"))
        assertThat(post?.subject, equalTo("Legacy Subject"))
        assertThat(subscription?.id, equalTo("sub-1"))
        assertThat(subscription?.name, equalTo("Legacy Subscription"))
        assertThat(subscription?.logoURL, equalTo(null))
        assertThat(postsForwardCursor, equalTo("legacy-cursor"))

        val sqliteDb = migratedDatabase.openHelper.writableDatabase
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cursors'").use {
            assertThat(it.count, equalTo(0))
        }
        assertThat(tableColumns(sqliteDb, "subscriptions"), equalTo(listOf("id", "name", "description", "optIn", "status", "logoURL")))
        assertThat(tableColumns(sqliteDb, "conversations"), equalTo(listOf("id", "subject", "lastReplyAt", "lastIncomingReplyAt", "lastIncomingParticipantID", "lastReadAt", "lastReadReplyID", "lastReplyPreview", "createdAt", "participantIDs", "updatedAt")))
        assertThat(tableColumns(sqliteDb, "replies"), equalTo(listOf("id", "conversationID", "senderType", "participantID", "externalID", "createdAt", "content", "syncState", "retryCount", "nextRetryAt", "lastSendError")))
        assertThat(tableColumns(sqliteDb, "participants"), equalTo(listOf("id", "name", "bio", "avatarUrl", "updatedAt")))
        assertThat(tableColumns(sqliteDb, "sync_states"), equalTo(listOf("roverEntity", "forwardCursor", "backwardCursor", "historyComplete")))
        assertThat(
            indexNames(sqliteDb, "replies").containsAll(
                listOf("index_replies_conversationID", "index_replies_participantID", "index_replies_externalID")
            ),
            equalTo(true)
        )
        assertThat(
            foreignKeys(sqliteDb, "replies"),
            equalTo(
                listOf(
                    ForeignKeyInfo(
                        from = "conversationID",
                        toTable = "conversations",
                        toColumn = "id",
                        onDelete = "CASCADE",
                    ),
                    ForeignKeyInfo(
                        from = "participantID",
                        toTable = "participants",
                        toColumn = "id",
                        onDelete = "SET NULL",
                    ),
                )
            )
        )

        sqliteDb.execSQL(
            "INSERT INTO conversations (id, subject, lastReplyAt, lastIncomingReplyAt, lastReadAt, lastReadReplyID, lastReplyPreview, createdAt, participantIDs, updatedAt) VALUES ('conv-1', 'Conversation Subject', 1704067200000, NULL, NULL, NULL, 'Preview', 1704067200000, '[]', 1704067200000)"
        )
        sqliteDb.execSQL(
            "INSERT INTO participants (id, name, bio, avatarUrl, updatedAt) VALUES ('participant-1', 'Participant Name', NULL, NULL, 1704067200000)"
        )
        sqliteDb.execSQL(
            "INSERT INTO replies (id, conversationID, senderType, participantID, externalID, createdAt, content) VALUES ('reply-1', 'conv-1', 'PARTICIPANT', 'participant-1', NULL, 1704067200000, '[]')"
        )
        sqliteDb.query("SELECT COUNT(*) AS count FROM replies WHERE id = 'reply-1'").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("count")), equalTo(1))
        }
    }

    @Test
    fun repliesEnforceUniqueNonNullExternalIdButAllowMultipleNulls() = runBlocking {
        createVersion1DatabaseWithLegacyData()

        val migratedDatabase = RoverEngageDatabase.getDatabase(context)
        val sqliteDb = migratedDatabase.openHelper.writableDatabase

        sqliteDb.execSQL(
            "INSERT INTO conversations (id, subject, lastReplyAt, lastIncomingReplyAt, lastReadAt, lastReadReplyID, lastReplyPreview, createdAt, participantIDs, updatedAt) VALUES ('conv-1', 'Subject', 1704067200000, NULL, NULL, NULL, 'Preview', 1704067200000, '[]', 1704067200000)"
        )

        // A fan reply persisted with its externalID idempotency key.
        sqliteDb.execSQL(
            "INSERT INTO replies (id, conversationID, senderType, participantID, externalID, createdAt, content) VALUES ('ext-1', 'conv-1', 'fan', NULL, 'ext-1', 1704067200000, '[]')"
        )

        // A second row carrying the same externalID must be rejected by the unique index.
        assertThrows(SQLiteConstraintException::class.java) {
            sqliteDb.execSQL(
                "INSERT INTO replies (id, conversationID, senderType, participantID, externalID, createdAt, content) VALUES ('server-1', 'conv-1', 'fan', NULL, 'ext-1', 1704067201000, '[]')"
            )
        }

        // Incoming (participant) replies carry a null externalID; multiple nulls are permitted.
        sqliteDb.execSQL(
            "INSERT INTO replies (id, conversationID, senderType, participantID, externalID, createdAt, content) VALUES ('reply-a', 'conv-1', 'participant', NULL, NULL, 1704067202000, '[]')"
        )
        sqliteDb.execSQL(
            "INSERT INTO replies (id, conversationID, senderType, participantID, externalID, createdAt, content) VALUES ('reply-b', 'conv-1', 'participant', NULL, NULL, 1704067203000, '[]')"
        )
        sqliteDb.query("SELECT COUNT(*) AS count FROM replies WHERE externalID IS NULL").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("count")), equalTo(2))
        }
    }

    @Test
    fun databaseDowngradeFromFutureVersionPerformsDestructiveResetForCacheData() {
        createFutureVersionDatabaseWithUnknownSchema()

        val downgradedDatabase = RoverEngageDatabase.getDatabase(context)
        val sqliteDb = downgradedDatabase.openHelper.writableDatabase

        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='future_table'").use {
            assertThat(it.count, equalTo(0))
        }
        sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sync_states'").use {
            assertThat(it.count, equalTo(1))
        }
    }

    private fun createVersion1DatabaseWithLegacyData() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS posts (id TEXT NOT NULL, subject TEXT NOT NULL, previewText TEXT NOT NULL, receivedAt INTEGER NOT NULL, url TEXT, isRead INTEGER NOT NULL, coverImageURL TEXT, subscription_id TEXT, PRIMARY KEY(id), FOREIGN KEY(subscription_id) REFERENCES subscriptions(id) ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS subscriptions (id TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, optIn INTEGER NOT NULL, status TEXT NOT NULL, PRIMARY KEY(id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cursors (roverEntity TEXT NOT NULL, cursor TEXT, PRIMARY KEY(roverEntity))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_posts_subscription_id ON posts (subscription_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '5fd036b69e12650a1377c8eccc3d5bc6')")

        db.execSQL(
            "INSERT INTO subscriptions (id, name, description, optIn, status) VALUES ('sub-1', 'Legacy Subscription', 'legacy-description', 1, 'published')"
        )
        db.execSQL(
            "INSERT INTO posts (id, subject, previewText, receivedAt, url, isRead, coverImageURL, subscription_id) VALUES ('post-1', 'Legacy Subject', 'legacy-preview', 123456, 'https://example.com', 0, NULL, 'sub-1')"
        )
        db.execSQL("INSERT INTO cursors (roverEntity, cursor) VALUES ('posts', 'legacy-cursor')")
        db.version = 1
        db.close()
    }

    private fun createFutureVersionDatabaseWithUnknownSchema() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) {
            dbFile.delete()
        }

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS future_table (id INTEGER PRIMARY KEY, value TEXT)")
        db.execSQL("INSERT INTO future_table (value) VALUES ('future-data')")
        db.version = 9999
        db.close()
    }

    private fun tableColumns(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): List<String> {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            val columnNames = mutableListOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(nameIndex))
            }
            return columnNames
        }
    }

    private fun indexNames(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): List<String> {
        db.query("PRAGMA index_list($tableName)").use { cursor ->
            val indexNames = mutableListOf<String>()
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(nameIndex))
            }
            return indexNames.sorted()
        }
    }

    private fun foreignKeys(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String): List<ForeignKeyInfo> {
        db.query("PRAGMA foreign_key_list($tableName)").use { cursor ->
            val foreignKeys = mutableListOf<ForeignKeyInfo>()
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val toIndex = cursor.getColumnIndexOrThrow("to")
            val onDeleteIndex = cursor.getColumnIndexOrThrow("on_delete")
            while (cursor.moveToNext()) {
                foreignKeys.add(
                    ForeignKeyInfo(
                        from = cursor.getString(fromIndex),
                        toTable = cursor.getString(tableIndex),
                        toColumn = cursor.getString(toIndex),
                        onDelete = cursor.getString(onDeleteIndex),
                    )
                )
            }
            return foreignKeys.sortedBy { it.from }
        }
    }

    private data class ForeignKeyInfo(
        val from: String,
        val toTable: String,
        val toColumn: String,
        val onDelete: String,
    )
}
