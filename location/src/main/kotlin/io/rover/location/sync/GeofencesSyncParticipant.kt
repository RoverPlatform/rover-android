package io.rover.location.sync

import android.annotation.SuppressLint
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.domain.ID
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.graphql.getStringIterable
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.sync.SyncDecoder
import io.rover.core.data.sync.PageInfo
import io.rover.core.data.sync.GraphQLResponse
import io.rover.core.data.sync.SqlSyncStorageInterface
import io.rover.core.data.sync.SyncResource
import io.rover.core.data.sync.SyncCoordinatorInterface
import io.rover.core.data.sync.SyncQuery
import io.rover.core.data.sync.SyncRequest
import io.rover.core.data.sync.after
import io.rover.core.data.sync.decodeJson
import io.rover.core.data.sync.first
import io.rover.core.logging.log
import io.rover.core.streams.map
import io.rover.core.streams.share
import io.rover.location.domain.Geofence
import org.json.JSONArray
import org.json.JSONObject
import org.reactivestreams.Publisher

/**
 * A Kotlin [Sequence], but one that must be closed once usage is complete.  Consider using [use].
 */
interface ClosableSequence<T>: Sequence<T>, AutoCloseable

class GeofencesRepository(
    private val syncCoordinator: SyncCoordinatorInterface,
    private val geofencesSqlStorage: GeofencesSqlStorage
) {
    /**
     * Be informed when geofences are available.
     *
     * Be sure to close the [ClosableSequence] when you are finished iterating through it.
     */
    fun allGeofences(): Publisher<ClosableSequence<Geofence>> = syncCoordinator.sync().map {
        // for now, we don't check the result because we just want an *attempt* to have completely
        // occurred.  In future we may keep state for tracking if at least one sync has happened
        // successfully over the install lifetime of the app, but for now, this will do.
        geofencesSqlStorage.queryAllGeofences()
    }.share()
}

class GeofencesSqlStorage(
    private val sqLiteDatabase: SQLiteDatabase
): SqlSyncStorageInterface<Geofence> {

    fun queryAllGeofences(): ClosableSequence<Geofence> {
        val columnNames = Columns.values().sortedBy { it.ordinal }.map { it.columnName }

        return object : ClosableSequence<Geofence> {

            // Responsibility for Recycling is delegated to the caller through the
            // [ClosableSequence].

            // TODO: change to only open the cursor when iteration begins.

            @SuppressLint("Recycle")
            val cursor = sqLiteDatabase.query(
                TABLE_NAME,
                columnNames.toTypedArray(),
                null,
                null,
                null,
                null,
                null
            )

            override fun iterator(): Iterator<Geofence> {
                cursor.moveToFirst()

                return object : AbstractIterator<Geofence>() {
                    override fun computeNext() {
                        if(!cursor.moveToNext()) {
                            done()
                        } else {
                            setNext(
                                Geofence(
                                    id = ID(cursor.getString(Columns.Id.ordinal)),
                                    center = Geofence.Center(
                                        cursor.getDouble(Columns.CenterLatitude.ordinal),
                                        cursor.getDouble(Columns.CenterLongitude.ordinal)
                                    ),
                                    name = cursor.getString(Columns.Name.ordinal),
                                    radius = cursor.getDouble(Columns.Radius.ordinal),
                                    tags = JSONArray(cursor.getString(Columns.Tags.ordinal)).getStringIterable().toList()
                                )
                            )
                        }
                    }
                }
            }

            override fun close() {
                cursor.close()
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
            sqLiteDatabase.setTransactionSuccessful();
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

class GeofencesSyncResource(
    private val sqliteStorageInterface: SqlSyncStorageInterface<Geofence>
): SyncResource<Geofence> {
    override fun upsertObjects(nodes: List<Geofence>) {
        // TODO: transform to domain object should happen here.
        sqliteStorageInterface.upsertObjects(nodes)
    }

    override fun nextRequest(cursor: String?): SyncRequest {
        log.v("Being asked for next sync request for cursor: $cursor")
        val values: HashMap<String, AttributeValue> = hashMapOf(Pair(SyncQuery.Argument.first.name, AttributeValue.Scalar.Integer(500)))

        if(cursor != null) {
            values[SyncQuery.Argument.after.name] = AttributeValue.Scalar.String(cursor)
        }

        return SyncRequest(
            SyncQuery.geofences,
            values
        )
    }
}

class GeofenceSyncDecoder: SyncDecoder<Geofence> {
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
            SyncQuery.Argument.first, SyncQuery.Argument.after
        ),
        fragments = listOf("geofenceFields")
    )
