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

package io.rover.sdk.notifications.communicationhub.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import io.rover.sdk.notifications.communicationhub.data.database.dao.CursorsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.PostsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.SubscriptionsDao
import io.rover.sdk.notifications.communicationhub.data.database.entities.CursorEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.SubscriptionEntity

@Database(
    entities = [
        PostEntity::class,
        SubscriptionEntity::class,
        CursorEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CommunicationHubTypeConverters::class)
abstract class CommunicationHubDatabase : RoomDatabase() {
    abstract fun postsDao(): PostsDao
    abstract fun subscriptionsDao(): SubscriptionsDao
    abstract fun cursorsDao(): CursorsDao

    companion object {
        @Volatile
        private var INSTANCE: CommunicationHubDatabase? = null

        fun getDatabase(context: Context): CommunicationHubDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CommunicationHubDatabase::class.java,
                    "communication_hub_database"
                )
                // in the event of a migration issue, wipe the DB entirely, don't crash.
                .fallbackToDestructiveMigration(true)

                    // uncomment to enable Room SQL query debug logging:
//                .setQueryCallback(
//                    object : androidx.room.RoomDatabase.QueryCallback {
//                        override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
//                             Log.d("CommunicationHubDatabase", "SQL Query: $sqlQuery SQL Args: $bindArgs")
//                        }
//                    },
//                    ContextCompat.getMainExecutor(context)
//                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}