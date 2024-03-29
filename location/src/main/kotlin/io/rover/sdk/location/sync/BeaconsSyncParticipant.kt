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

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import io.rover.sdk.core.data.domain.ID
import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.graphql.getStringIterable
import io.rover.sdk.core.data.graphql.safeGetString
import io.rover.sdk.core.data.sync.GraphQLResponse
import io.rover.sdk.core.data.sync.PageInfo
import io.rover.sdk.core.data.sync.SqlSyncStorageInterface
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.data.sync.SyncDecoder
import io.rover.sdk.core.data.sync.SyncQuery
import io.rover.sdk.core.data.sync.SyncRequest
import io.rover.sdk.core.data.sync.SyncResource
import io.rover.sdk.core.data.sync.after
import io.rover.sdk.core.data.sync.decodeJson
import io.rover.sdk.core.data.sync.first
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.subscribeOn
import io.rover.sdk.location.domain.Beacon
import org.json.JSONArray
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.util.HashMap
import java.util.UUID

class BeaconsRepository(
    private val syncCoordinator: SyncCoordinatorInterface,
    private val beaconsSqlStorage: BeaconsSqlStorage,
    private val ioScheduler: Scheduler
) {
    fun allBeacons(): Publisher<ClosableSequence<Beacon>> =
        Publishers.concat(
            Publishers.defer {
                Publishers.just(beaconsSqlStorage.queryAllBeacons())
            },
            syncCoordinator
                .updates
                .observeOn(ioScheduler)
                .map {
                    beaconsSqlStorage.queryAllBeacons()
                }
        )


    /**
     * Look up a [Beacon] by its iBeacon UUID, major, and minor.  Yields it if it exists.
     */
    fun beaconByUuidMajorAndMinor(uuid: UUID, major: Short, minor: Short): Publisher<Beacon?> {
        return Publishers.defer {
            Publishers.just(beaconsSqlStorage.queryBeaconByUuidMajorAndMinor(uuid, major, minor))
        }.subscribeOn(ioScheduler)
    }
}

class BeaconsSqlStorage(
    private val sqLiteDatabase: SQLiteDatabase
) : SqlSyncStorageInterface<Beacon> {

    fun queryBeaconByUuidMajorAndMinor(uuid: UUID, major: Short, minor: Short): Beacon? {
        val columnNames = Columns.values().sortedBy { it.ordinal }.map { it.columnName }

        val cursor = sqLiteDatabase.query(
            TABLE_NAME,
            columnNames.toTypedArray(),
            "uuid = ? AND major = ? AND minor = ?",
            arrayOf(uuid.toString(), major.toString(), minor.toString()),
            null,
            null,
            null
        )

        return try {
            if (cursor.moveToFirst()) {
                return Beacon.fromSqliteCursor(cursor)
            } else {
                null
            }
        } catch (e: SQLiteException) {
            log.w("Unable to query DB for beacon: $e")
            null
        } finally {
            cursor.close()
        }
    }

    fun queryAllBeacons(): ClosableSequence<Beacon> {
        val columnNames = Columns.values().sortedBy { it.ordinal }.map { it.columnName }

        return object : ClosableSequence<Beacon> {
            // Responsibility for Recycling is delegated to the caller through the
            // [ClosableSequence].

            override fun iterator(): CloseableIterator<Beacon> {
                val cursor = sqLiteDatabase.query(
                    TABLE_NAME,
                    columnNames.toTypedArray(),
                    null,
                    null,
                    null,
                    null,
                    null
                )
                return object : AbstractIterator<Beacon>(), CloseableIterator<Beacon> {
                    override fun computeNext() {
                        if (!cursor.moveToNext()) {
                            done()
                        } else {
                            setNext(
                                Beacon.fromSqliteCursor(cursor)
                            )
                        }
                    }

                    override fun close() {
                        cursor.close()
                    }
                }
            }
        }
    }

    // TODO: indicate failure.  right now cursor will still be moved forward.
    override fun upsertObjects(items: List<Beacon>) {
        sqLiteDatabase.beginTransaction()

        try {
            items.forEach { item ->
                val initialValues = ContentValues().apply {
                    put(Columns.Id.columnName, item.id.rawValue)
                    put(Columns.Name.columnName, item.name)
                    put(Columns.Uuid.columnName, item.uuid.toString())
                    put(Columns.Major.columnName, item.major)
                    put(Columns.Minor.columnName, item.minor)
                    put(Columns.Tags.columnName, JSONArray(item.tags).toString())
                }

                sqLiteDatabase.insertWithOnConflict(
                    TABLE_NAME,
                    null,
                    initialValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            sqLiteDatabase.setTransactionSuccessful()
        } catch (e: SQLiteException) {
            log.w("Problem upserting beacon into sync database: %s")
        } finally {
            sqLiteDatabase.endTransaction()
        }
    }

    companion object {
        private const val TABLE_NAME = "beacons"

        fun initSchema(sqLiteDatabase: SQLiteDatabase) {
            sqLiteDatabase.execSQL(
                """
                CREATE TABLE $TABLE_NAME (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    uuid TEXT,
                    major INTEGER,
                    minor INTEGER,
                    tags TEXT
                )
                """.trimIndent()
            )
        }

        fun dropSchema(sqLiteDatabase: SQLiteDatabase) {
            try {
                sqLiteDatabase.execSQL(
                    """
                DROP TABLE $TABLE_NAME
                    """.trimIndent()
                )
            } catch (e: SQLiteException) {
                log.w("Unable to drop existing table, assuming it does not exist: $e")
            }
        }
    }

    enum class Columns(
        val columnName: String
    ) {
        Id("id"),
        Name("name"),
        Uuid("uuid"),
        Major("major"),
        Minor("minor"),
        Tags("tags")
    }
}

fun Beacon.Companion.fromSqliteCursor(cursor: Cursor): Beacon {
    return Beacon(
        id = ID(cursor.getString(BeaconsSqlStorage.Columns.Id.ordinal)),
        name = cursor.getString(BeaconsSqlStorage.Columns.Name.ordinal),
        uuid = UUID.fromString(cursor.getString(BeaconsSqlStorage.Columns.Uuid.ordinal)),
        major = cursor.getInt(BeaconsSqlStorage.Columns.Major.ordinal),
        minor = cursor.getInt(BeaconsSqlStorage.Columns.Minor.ordinal),
        tags = JSONArray(cursor.getString(BeaconsSqlStorage.Columns.Tags.ordinal)).getStringIterable().toList()
    )
}

class BeaconsSyncResource(
    private val sqliteStorageInterface: SqlSyncStorageInterface<Beacon>
) : SyncResource<Beacon> {
    override fun upsertObjects(nodes: List<Beacon>) {
        sqliteStorageInterface.upsertObjects(nodes)
    }

    override fun nextRequest(cursor: String?): SyncRequest {
        log.v("Being asked for next sync request for cursor: $cursor")

        val values: HashMap<String, Any> = hashMapOf(
            Pair(SyncQuery.Argument.first.name, 500),
            Pair(
                SyncQuery.Argument.orderBy.name,
                hashMapOf(
                    Pair("field", "UPDATED_AT"),
                    Pair("direction", "ASC")
                )
            )
        )

        if (cursor != null) {
            values[SyncQuery.Argument.after.name] = cursor
        }

        return SyncRequest(
            SyncQuery.beacons,
            values
        )
    }
}

class BeaconSyncDecoder : SyncDecoder<Beacon> {
    override fun decode(json: JSONObject): GraphQLResponse<Beacon> {
        return BeaconSyncResponseData.decodeJson(json.getJSONObject("data")).beacons
    }
}

data class BeaconSyncResponseData(
    val beacons: GraphQLResponse<Beacon>
) {
    companion object
}

fun BeaconSyncResponseData.Companion.decodeJson(json: JSONObject): BeaconSyncResponseData {
    return BeaconSyncResponseData(
        beacons = GraphQLResponse.decodeBeaconPageJson(
            json.getJSONObject("beacons")
        )
    )
}

fun GraphQLResponse.Companion.decodeBeaconPageJson(json: JSONObject): GraphQLResponse<Beacon> {
    return GraphQLResponse(
        nodes = json.getJSONArray("nodes").getObjectIterable().map { nodeJson ->
            Beacon.decodeJson(nodeJson)
        },
        pageInfo = PageInfo.decodeJson(
            json.getJSONObject("pageInfo")
        )
    )
}

fun Beacon.Companion.decodeJson(json: JSONObject): Beacon {
    return Beacon(
        ID(json.safeGetString("id")),
        json.safeGetString("name"),
        UUID.fromString(json.safeGetString("uuid")),
        json.getInt("major"),
        json.getInt("minor"),
        json.getJSONArray("tags").getStringIterable().toList()
    )
}

val SyncQuery.Companion.beacons: SyncQuery
    get() = SyncQuery(
        "beacons",
        """
            nodes {
                ...beaconFields
            }
            pageInfo {
                endCursor
                hasNextPage
            }
        """.trimIndent(),
        arguments = listOf(
            SyncQuery.Argument.first,
            SyncQuery.Argument.after,
            SyncQuery.Argument.orderBy
        ),
        fragments = listOf("beaconFields")
    )

private val SyncQuery.Argument.Companion.orderBy
    get() = SyncQuery.Argument("orderBy", "BeaconOrder")
