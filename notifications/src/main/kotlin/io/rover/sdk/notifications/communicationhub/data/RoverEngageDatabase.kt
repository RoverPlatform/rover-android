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
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.conversations.ConversationEntity
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsDao
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantEntity
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantsDao
import io.rover.sdk.notifications.communicationhub.conversations.ReplyEntity
import io.rover.sdk.notifications.communicationhub.conversations.RepliesDao
import io.rover.sdk.notifications.communicationhub.posts.PostEntity
import io.rover.sdk.notifications.communicationhub.posts.PostsDao
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionEntity
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionsDao
import io.rover.sdk.notifications.communicationhub.sync.SyncStateDao
import io.rover.sdk.notifications.communicationhub.sync.SyncStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [
        PostEntity::class,
        SubscriptionEntity::class,
        ConversationEntity::class,
        ReplyEntity::class,
        ParticipantEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(RoverEngageTypeConverters::class)
internal abstract class RoverEngageDatabase : RoomDatabase() {
    abstract fun postsDao(): PostsDao
    abstract fun subscriptionsDao(): SubscriptionsDao
    abstract fun conversationsDao(): ConversationsDao
    abstract fun repliesDao(): RepliesDao
    abstract fun participantsDao(): ParticipantsDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `subscriptions` ADD COLUMN `logoURL` TEXT"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `subject` TEXT, `lastReplyAt` INTEGER NOT NULL, `lastIncomingReplyAt` INTEGER, `lastIncomingParticipantID` TEXT, `lastReadAt` INTEGER, `lastReadReplyID` TEXT, `lastReplyPreview` TEXT, `createdAt` INTEGER NOT NULL, `participantIDs` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `participants` (`id` TEXT NOT NULL, `name` TEXT, `bio` TEXT, `avatarUrl` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `replies` (`id` TEXT NOT NULL, `conversationID` TEXT NOT NULL, `senderType` TEXT NOT NULL, `participantID` TEXT, `externalID` TEXT, `createdAt` INTEGER NOT NULL, `content` TEXT NOT NULL, `syncState` TEXT NOT NULL DEFAULT 'confirmed', `retryCount` INTEGER NOT NULL DEFAULT 0, `nextRetryAt` INTEGER, `lastSendError` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`conversationID`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`participantID`) REFERENCES `participants`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_replies_conversationID` ON `replies` (`conversationID`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_replies_participantID` ON `replies` (`participantID`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_replies_externalID` ON `replies` (`externalID`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_states` (`roverEntity` TEXT NOT NULL, `forwardCursor` TEXT, `backwardCursor` TEXT, `historyComplete` INTEGER NOT NULL, PRIMARY KEY(`roverEntity`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `sync_states` (`roverEntity`, `forwardCursor`, `backwardCursor`, `historyComplete`) SELECT `roverEntity`, `cursor`, NULL, 0 FROM `cursors`"
                )
                db.execSQL("DROP TABLE `cursors`")
            }
        }

        @Volatile
        private var INSTANCE: RoverEngageDatabase? = null

        fun getDatabase(context: Context): RoverEngageDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildInstance(context).also { INSTANCE = it }
            }
        }

        private fun buildInstance(context: Context, dropFirst: Boolean = false): RoverEngageDatabase {
            if (dropFirst) {
                context.deleteDatabase("rover_engage_database")
                log.w("rover_engage_database dropped due to schema integrity mismatch; recreating.")
            }

            // side effect, delete the DB with the previous name if it still exists
            CoroutineScope(Dispatchers.IO).launch {
                if (context.deleteDatabase("communication_hub_database")) {
                    log.i("Deleted legacy communication_hub_database")
                }
            }

            val instance = Room.databaseBuilder(
                context.applicationContext,
                RoverEngageDatabase::class.java,
                "rover_engage_database",
            )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade(true)

                // uncomment to enable Room SQL query debug logging:
//            .setQueryCallback(
//                object : androidx.room.RoomDatabase.QueryCallback {
//                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
//                         Log.d("CommunicationHubDatabase", "SQL Query: $sqlQuery SQL Args: $bindArgs")
//                    }
//                },
//                ContextCompat.getMainExecutor(context)
//            )
            .build()

            // Eagerly force-open the DB to detect schema integrity errors (identity hash mismatch)
            // before the instance is handed out. Room's checkIdentity throws IllegalStateException
            // in this case, which would otherwise crash the app later at an arbitrary call site.
            // openHelper.writableDatabase bypasses Room's main-thread guard and is safe here.
            if (!dropFirst) {
                try {
                    instance.openHelper.writableDatabase
                } catch (e: IllegalStateException) {
                    if (e.message?.contains("cannot verify the data integrity") == true) {
                        log.e("rover_engage_database schema integrity check failed, will drop and recreate: ${e.message}")
                        instance.close()
                        return buildInstance(context, dropFirst = true)
                    }
                    throw e
                }
            }

            return instance
        }
    }
}
