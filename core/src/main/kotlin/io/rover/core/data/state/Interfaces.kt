package io.rover.core.data.state

import io.rover.core.data.NetworkResult
import org.reactivestreams.Publisher
import org.json.JSONObject

/**
 * This service is responsible for making timely requests to the endpoint for [DeviceState]s.  These
 * Device State can contain data from multiple concerns in the Rover SDK, so rather than deserialize
 * and persist them all here we instead delegate to any registered observers to do so.
 */
interface StateManagerServiceInterface {
    /**
     * Add a state observer to the state manager.  The observer will be responsible for parsing out
     * and perhaps caching/persisting the payload relevant to its vertical.
     *
     * @param queryFragment A GraphQL query fragment.
     *
     * @param neededPersistedFragments The names of any GraphQL persisted fragments that
     * [queryFragment] makes reference to.
     */
    fun updatesForQueryFragment(
        queryFragment: String,
        neededPersistedFragments: List<String>
    ): Publisher<NetworkResult<JSONObject>>

    /**
     * Begin a refresh now, perhaps because of a refresh action.
     */
    fun triggerRefresh()
}
