package io.rover.campaigns.location.sync

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import io.rover.campaigns.core.data.domain.ID
import io.rover.campaigns.core.data.graphql.getObjectIterable
import io.rover.campaigns.core.data.graphql.getStringIterable
import io.rover.campaigns.core.data.graphql.safeGetString
import io.rover.campaigns.core.data.sync.GraphQLResponse
import io.rover.campaigns.core.data.sync.PageInfo
import io.rover.campaigns.core.data.sync.SqlSyncStorageInterface
import io.rover.campaigns.core.data.sync.SyncCoordinatorInterface
import io.rover.campaigns.core.data.sync.SyncDecoder
import io.rover.campaigns.core.data.sync.SyncQuery
import io.rover.campaigns.core.data.sync.SyncRequest
import io.rover.campaigns.core.data.sync.SyncResource
import io.rover.campaigns.core.data.sync.after
import io.rover.campaigns.core.data.sync.decodeJson
import io.rover.campaigns.core.data.sync.first
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.streams.Publishers
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.core.streams.map
import io.rover.campaigns.core.streams.observeOn
import io.rover.campaigns.core.streams.subscribeOn
import io.rover.campaigns.location.domain.Geofence
import org.json.JSONArray
import org.json.JSONObject
import org.reactivestreams.Publisher

class GeofencesRepository(
    private val syncCoordinator: SyncCoordinatorInterface,
    private val geofencesSqlStorage: GeofencesSqlStorage,
    private val ioScheduler: Scheduler
) {
    /**
     * Be informed when geofences are available.
     *
     * Be sure to close the [ClosableSequence] when you are finished iterating through it.
     */
    fun allGeofences(): Publisher<ClosableSequence<Geofence>> = syncCoordinator
        .updates
        .observeOn(ioScheduler)
        .map {
            // for now, we don't check the result because we just want an *attempt* to have completely
            // occurred.  In future we may keep state for tracking if at least one sync has happened
            // successfully over the install lifetime of the app, but for now, this will do.
            geofencesSqlStorage.queryAllGeofences()
        }

    fun geofenceByIdentifier(identifier: String): Publisher<Geofence?> {
        return Publishers.defer {
            Publishers.just(
                geofencesSqlStorage.queryGeofenceByIdentifier(identifier)
            )
        }.subscribeOn(ioScheduler)
    }
}

class GeofencesSqlStorage(
    private val sqLiteDatabase: SQLiteDatabase
) : SqlSyncStorageInterface<Geofence> {

    fun queryGeofenceByIdentifier(identifier: String): Geofence? {
        val columnNames = Columns.values().sortedBy { it.ordinal }.map { it.columnName }

        val queryByIdentifierComponents = Geofence.IdentiferComponents(identifier)

        val cursor = sqLiteDatabase.query(
            TABLE_NAME,
            columnNames.toTypedArray(),
            "${Columns.CenterLatitude.columnName} = ? AND ${Columns.CenterLongitude.columnName} = ? AND ${Columns.Radius.columnName} = ?",
            arrayOf(
                queryByIdentifierComponents.latitude.toString(),
                queryByIdentifierComponents.longitude.toString(),
                queryByIdentifierComponents.radius.toString()
            ),
            null,
            null,
            null
        )

        return try {
            if (cursor.moveToFirst()) {
                return Geofence.fromSqliteCursor(cursor)
            } else {
                null
            }
        } catch (e: SQLiteException) {
            log.w("Unable to query DB for geofence: $e")
            null
        } finally {
            cursor.close()
        }
    }

    fun queryAllGeofences(): ClosableSequence<Geofence> {
        val columnNames = Columns.values().sortedBy { it.ordinal }.map { it.columnName }

        return object : ClosableSequence<Geofence> {
            // Responsibility for Recycling is delegated to the caller through the
            // [ClosableSequence].
            @SuppressLint("Recycle")
            override fun iterator(): CloseableIterator<Geofence> {
                val cursor = sqLiteDatabase.query(
                    TABLE_NAME,
                    columnNames.toTypedArray(),
                    null,
                    null,
                    null,
                    null,
                    null
                )

                return object : AbstractIterator<Geofence>(), CloseableIterator<Geofence> {
                    override fun computeNext() {
                        if (!cursor.moveToNext()) {
                            done()
                        } else {
                            setNext(
                                Geofence.fromSqliteCursor(cursor)
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

    // TODO: indicate failure. right now cursor will still be moved forward.
    override fun upsertObjects(items: List<Geofence>) {
        sqLiteDatabase.beginTransaction()

        try {
            items.forEach { item ->
                val initialValues = ContentValues().apply {
                    put(Columns.Id.columnName, item.id.rawValue)
                    put(Columns.Name.columnName, item.name)
                    put(Columns.Radius.columnName, item.radius)
                    put(Columns.Tags.columnName, JSONArray(item.tags).toString())
                    put(Columns.CenterLatitude.columnName, item.center.latitude)
                    put(Columns.CenterLongitude.columnName, item.center.longitude)
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
            // TODO: perhaps this should be a harder error, and perhaps trigger a full resync
            // or similar recovery behaviour.  or Perhaps abort transaction and also prevent update of the cursor so it can be attempted again?
            log.w("Problem upserting geofence into sync database: $e")
        } finally {
            sqLiteDatabase.endTransaction()
        }
    }

    companion object {
        private const val TABLE_NAME = "geofences"

        fun initSchema(sqLiteDatabase: SQLiteDatabase) {
            sqLiteDatabase.execSQL("""
                CREATE TABLE $TABLE_NAME (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    radius DOUBLE,
                    tags TEXT,
                    center_latitude DOUBLE,
                    center_longitude DOUBLE
                )
        """.trimIndent())
        }

        fun dropSchema(sqLiteDatabase: SQLiteDatabase) {
            sqLiteDatabase.execSQL("""
                DROP TABLE $TABLE_NAME
            """.trimIndent())
        }
    }

    enum class Columns(
        val columnName: String
    ) {
        Id("id"),
        Name("name"),
        Radius("radius"),
        Tags("tags"),
        CenterLatitude("center_latitude"),
        CenterLongitude("center_longitude")
    }
}

fun Geofence.Companion.fromSqliteCursor(cursor: Cursor): Geofence {
    return Geofence(
        id = ID(cursor.getString(GeofencesSqlStorage.Columns.Id.ordinal)),
        center = Geofence.Center(
            cursor.getDouble(GeofencesSqlStorage.Columns.CenterLatitude.ordinal),
            cursor.getDouble(GeofencesSqlStorage.Columns.CenterLongitude.ordinal)
        ),
        name = cursor.getString(GeofencesSqlStorage.Columns.Name.ordinal),
        radius = cursor.getDouble(GeofencesSqlStorage.Columns.Radius.ordinal),
        tags = JSONArray(cursor.getString(GeofencesSqlStorage.Columns.Tags.ordinal)).getStringIterable().toList()
    )
}

class GeofencesSyncResource(
    private val sqliteStorageInterface: SqlSyncStorageInterface<Geofence>
) : SyncResource<Geofence> {
    override fun upsertObjects(nodes: List<Geofence>) {
        sqliteStorageInterface.upsertObjects(nodes)
    }

    override fun nextRequest(cursor: String?): SyncRequest {
        log.v("Being asked for next sync request for cursor: $cursor")
        val values: HashMap<String, Any> = hashMapOf(
            Pair(SyncQuery.Argument.first.name, 500),
            Pair(SyncQuery.Argument.orderBy.name, hashMapOf(
                Pair("field", "UPDATED_AT"),
                Pair("direction", "ASC")
            ))
        )

        if (cursor != null) {
            values[SyncQuery.Argument.after.name] = cursor
        }

        return SyncRequest(
            SyncQuery.geofences,
            values
        )
    }
}

class GeofenceSyncDecoder : SyncDecoder<Geofence> {
    override fun decode(json: JSONObject): GraphQLResponse<Geofence> {
        return GeofencesSyncResponseData.decodeJson(json.getJSONObject("data")).geofences
    }
}

data class GeofencesSyncResponseData(
    val geofences: GraphQLResponse<Geofence>
) {
    companion object
}

fun GeofencesSyncResponseData.Companion.decodeJson(json: JSONObject): GeofencesSyncResponseData {
    return GeofencesSyncResponseData(
        geofences = GraphQLResponse.decodeGeofencePageJson(
            json.getJSONObject("geofences")
        )
    )
}

fun GraphQLResponse.Companion.decodeGeofencePageJson(json: JSONObject): GraphQLResponse<Geofence> {
    return GraphQLResponse(
        nodes = json.getJSONArray("nodes").getObjectIterable().map { nodeJson ->
            Geofence.decodeJson(nodeJson)
        },
        pageInfo = PageInfo.decodeJson(
            json.getJSONObject("pageInfo")
        )
    )
}

fun Geofence.Companion.decodeJson(json: JSONObject): Geofence {
    return Geofence(
        json.getJSONObject("center").let { centerJson ->
            Geofence.Center(
                centerJson.getDouble("latitude"),
                centerJson.getDouble("longitude")
            )
        },
        ID(json.safeGetString("id")),
        json.safeGetString("name"),
        json.getDouble("radius"),
        json.getJSONArray("tags").getStringIterable().toList()
    )
}

val SyncQuery.Companion.geofences: SyncQuery
    get() = SyncQuery(
        "geofences",
        """
            nodes {
                ...geofenceFields
            }
            pageInfo {
                endCursor
                hasNextPage
            }
            """.trimIndent(),

        arguments = listOf(
            SyncQuery.Argument.first, SyncQuery.Argument.after, SyncQuery.Argument.orderBy
        ),
        fragments = listOf("geofenceFields")
    )

private val SyncQuery.Argument.Companion.orderBy
    get() = SyncQuery.Argument("orderBy", "GeofenceOrder")
