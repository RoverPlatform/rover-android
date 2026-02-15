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

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.rover.sdk.core.logging.log
import io.rover.sdk.notifications.communicationhub.data.database.dao.CursorsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.PostsDao
import io.rover.sdk.notifications.communicationhub.data.database.dao.SubscriptionsDao
import io.rover.sdk.notifications.communicationhub.data.database.entities.CursorEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.SubscriptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Database(
    entities = [
        PostEntity::class,
        SubscriptionEntity::class,
        CursorEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoverEngageTypeConverters::class)
abstract class RoverEngageDatabase : RoomDatabase() {
    abstract fun postsDao(): PostsDao
    abstract fun subscriptionsDao(): SubscriptionsDao
    abstract fun cursorsDao(): CursorsDao

    companion object {
        @Volatile
        private var INSTANCE: RoverEngageDatabase? = null


        fun getDatabase(context: Context): RoverEngageDatabase {
            return INSTANCE ?: synchronized(this) {
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
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)

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