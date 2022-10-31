package io.rover.campaigns.core.data.sync

import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.whenNotNull
import org.json.JSONException
import org.json.JSONObject
import java.lang.RuntimeException
import java.util.Date

interface CursorState {
    fun cursorForKey(key: String): String?

    fun setCursorForKey(key: String, cursor: String)
}

/**
 * These objects provide the sync infrastructure with the means of persisting the synced records.
 */
interface SqlSyncStorageInterface<TItem> {
    fun upsertObjects(items: List<TItem>)
}

/**
 * Responsible for storage of synced resources of type [TNode], and also for constructing
 * [SyncRequest]s for the resource.
 */
interface SyncResource<TNode> {
    /**
     * Upsert the given objects into local storage.
     */
    fun upsertObjects(nodes: List<TNode>)

    /**
     * Builds a sync request for the given cursor.
     */
    fun nextRequest(cursor: String?): SyncRequest
}

interface SyncDecoder<TNode> {
    /**
     * May throw [JSONException].
     */
    fun decode(json: JSONObject): GraphQLResponse<TNode>
}

class RealPagedSyncParticipant<TNode>(
    private val syncResource: SyncResource<TNode>,
    private val syncDecoder: SyncDecoder<TNode>,
    private val cursorKey: String? = null,
    private val cursorState: CursorState? = null
) : SyncParticipant {

    init {
        if ((cursorKey != null) xor (cursorState != null)) {
            throw RuntimeException("If you decide to support the use of cursors in this RealPagedSyncParticipant, then you must pass both cursorKey and cursorState.")
        }
    }

    override fun initialRequest(): SyncRequest {
        return syncResource.nextRequest(
            cursorKey.whenNotNull { cursorState?.cursorForKey(it) }
        )
    }

    override fun saveResponse(json: JSONObject): SyncResult {

        // deserialization.  who shall be responsible for it?  Could I
        // move the concern down into storagethingy?

        val pagingResponse = try {
            syncDecoder.decode(json)
        } catch (e: JSONException) {
            log.w("Failed to decode sync JSON: $e")
            return SyncResult.Failed
        }

        if (pagingResponse.nodes.isEmpty()) {
            return SyncResult.NoData
        }

        val upsertStartTime = Date()
        syncResource.upsertObjects(pagingResponse.nodes)
        log.v("Took ${Date().time - upsertStartTime.time} ms to insert all records in this sync batch.")

        updateCursor(pagingResponse)

        return result(pagingResponse)
    }

    private fun updateCursor(graphQLResponse: GraphQLResponse<TNode>) {
        if (graphQLResponse.pageInfo?.endCursor?.isNotBlank() == true) {
            cursorKey.whenNotNull {
                log.v("Updating cursor to be ${graphQLResponse.pageInfo.endCursor}")
                cursorState?.setCursorForKey(it, graphQLResponse.pageInfo.endCursor)
            }
        }
    }

    private fun result(graphQLResponse: GraphQLResponse<TNode>): SyncResult {
        return when {
            graphQLResponse.nodes.isEmpty() -> SyncResult.NoData
            graphQLResponse.pageInfo?.hasNextPage == false -> SyncResult.NewData(
                null
            )
            cursorKey == null -> {
                // paging not enabled, so no nextRequest.
                SyncResult.NewData(null)
            }
            else -> SyncResult.NewData(
                syncResource.nextRequest(cursorKey.whenNotNull { cursorState?.cursorForKey(it) })
            )
        }
    }
}
