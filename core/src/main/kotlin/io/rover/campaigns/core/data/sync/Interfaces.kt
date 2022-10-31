package io.rover.campaigns.core.data.sync

import io.rover.campaigns.core.data.domain.Attributes
import io.rover.campaigns.core.data.http.HttpClientResponse
import org.json.JSONObject
import org.reactivestreams.Publisher

/**
 * Yielded by the GraphQL API for each page of data.
 *
 * PageInfo may be null if paging is not being used.
 */
data class GraphQLResponse<TNode>(
    val nodes: List<TNode>,
    val pageInfo: PageInfo?
) {
    companion object
}

data class PageInfo(
    val endCursor: String?,
    val hasNextPage: Boolean
) {
    companion object
}

data class SyncQuery(
    val name: String,
    val body: String,
    val arguments: List<Argument>,
    val fragments: List<String>
) {
    data class Argument(
        val name: String,
        val type: String
    ) {
        companion object
    }

    companion object
}

val SyncQuery.Argument.Companion.first
    get() = SyncQuery.Argument("first", "Int")

val SyncQuery.Argument.Companion.after
    get() = SyncQuery.Argument("after", "String")

val SyncQuery.Argument.Companion.last
    get() = SyncQuery.Argument("last", "Int")

val SyncQuery.Argument.Companion.before
    get() = SyncQuery.Argument("before", "String")

data class SyncRequest(
    val query: SyncQuery,
    val variables: Attributes
) {
    companion object
}

/**
 * Yielded by SyncParticipants to indicate if they have more data to retrieve in subsequent passes
 * of a paginated sync run.
 */
sealed class SyncResult {
    class NewData(val nextRequest: SyncRequest?) : SyncResult()
    object NoData : SyncResult()
    object Failed : SyncResult()
}

interface SyncClientInterface {
    fun executeSyncRequests(requests: List<SyncRequest>): Publisher<HttpClientResponse>
}

// and then the actual interface and service types:

interface SyncCoordinatorInterface {
    /**
     * Adds a [SyncParticipant] to be included in GraphQL-powered sync runs.
     */
    fun registerParticipant(participant: SyncParticipant)

    /**
     * Trigger an immediate sync with all of the registered participants.
     *
     * (This will start each participant at its initial sync request).
     *
     * Will remain subscribed afterwards so that you can discover subsequent sync results.
     */
    @Deprecated("This method can cause unnecessary syncs to be triggered.  Consider separate usages of triggerSync() and one of the 'events' or 'syncs' properties.")
    fun sync(): Publisher<Result>

    /**
     * Subscribe to be informed of when Syncs complete or fail.
     */
    val syncResults: Publisher<Result>

    /**
     * Subscribe to be informed of when a Sync has been completed.  Use this to ensure that
     * something downstream is kept update whenever a sync completes.
     */
    val updates: Publisher<Unit>

    /**
     * Asynchronously trigger a sync without being interested in a response.
     */
    fun triggerSync()

    /**
     * Use Android's WorkManager to schedule ongoing syncs.
     */
    fun ensureBackgroundSyncScheduled()

    enum class Result {
        Succeeded, RetryNeeded
    }
}

interface SyncParticipant {
    fun initialRequest(): SyncRequest?
    fun saveResponse(json: JSONObject): SyncResult
}
