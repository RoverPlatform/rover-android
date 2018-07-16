package io.rover.location

import io.rover.location.domain.Region
import io.rover.location.graphql.data.decodeJson
import io.rover.core.data.NetworkResult
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.state.StateManagerServiceInterface
import io.rover.core.logging.log
import io.rover.core.streams.Scheduler
import io.rover.core.streams.filterNulls
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribe
import org.json.JSONObject

// TODO: this isn't really a full repository, rename to RegionStore.
class RegionRepository(
    stateManagerService: StateManagerServiceInterface,
    mainThreadScheduler: Scheduler
): RegionRepositoryInterface {
    override fun registerObserver(regionObserver: RegionObserver) {
        updates.subscribe { regions ->
            regionObserver.regionsUpdated(regions)
        }
    }

    private val queryFragment = """
        regions {
            ...regionFields
        }
    """

    private fun decodeRegionsPayload(data: JSONObject): List<Region> =
        data.getJSONArray("regions").getObjectIterable().map { regionJson -> Region.decodeJson(regionJson) }

    private val updates = stateManagerService.updatesForQueryFragment(
        queryFragment,
        listOf("regionFields")
    )
        .map { networkResult ->
            when(networkResult) {
                is NetworkResult.Success -> decodeRegionsPayload(networkResult.response)
                is NetworkResult.Error -> {
                    log.v("Regions not updated due to: ${networkResult.throwable.message}")
                    null
                }
            }
        }
        .filterNulls()
        .observeOn(mainThreadScheduler)
        .shareHotAndReplay(1)
}
