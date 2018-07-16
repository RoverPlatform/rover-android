package io.rover.core.data.state

import android.os.Handler
import android.os.Looper
import io.rover.core.data.GraphQlRequest
import io.rover.core.data.NetworkResult
import io.rover.core.data.graphql.GraphQlApiServiceInterface
import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.doOnNext
import io.rover.core.streams.flatMap
import io.rover.core.streams.share
import io.rover.core.platform.DeviceIdentificationInterface
import org.json.JSONObject
import org.reactivestreams.Publisher

class StateManagerService(
    private val deviceIdentification: DeviceIdentificationInterface,
    private val graphQlApiService: GraphQlApiServiceInterface,
    autoFetch: Boolean = true
): StateManagerServiceInterface {
    private val deviceIdentifer = deviceIdentification.installationIdentifier

    override fun updatesForQueryFragment(queryFragment: String, neededPersistedFragments: List<String>): Publisher<NetworkResult<JSONObject>> {
        queryFragments.add(queryFragment)
        this.neededPersistedFragments.addAll(neededPersistedFragments)

        // TODO: consider caching and re-emitting latest device state to all new subscribers.
        return updates
    }

    override fun triggerRefresh() {
        log.v("Performing refresh.")

        actionSubject.onNext(Unit)
    }

    private val queryFragments: MutableSet<String> = mutableSetOf()
    private val neededPersistedFragments: MutableSet<String> = mutableSetOf()

    private val actionSubject =  PublishSubject<Unit>()

    private val updates = actionSubject.flatMap {

        graphQlApiService.operation(
            DeviceStateNetworkRequest(deviceIdentifer, queryFragments, neededPersistedFragments.toList())
        )
    }.doOnNext { update ->
        if(update is NetworkResult.Error) {
            log.w("Unable to update device state because: ${update}")
        }
    }
    .share()

    init {
        // trigger refresh for the next loop of the Android main looper.  This will happen
        // after all of the Rover DI has completed and thus all of the stores have been registered.

        if(autoFetch) {
            Handler(Looper.getMainLooper()).post {
                triggerRefresh()
            }
        }
    }
}

private class DeviceStateNetworkRequest(
    private val deviceIdentifier: String,
    private val queryFragments: Set<String>,
    override val fragments: List<String>
): GraphQlRequest<JSONObject> {
    override val operationName: String? = "StateRefresh"

    override val mutation: Boolean = false

    override val variables: JSONObject = JSONObject().apply {
        put("identifier", deviceIdentifier)
    }

    override fun decodePayload(responseObject: JSONObject): JSONObject {
        return responseObject.getJSONObject("data").getJSONObject("device")
    }

    override val query: String
        get() = """
            query $operationName(${"\$"}identifier: String!) {
                device(identifier: ${"\$"}identifier) {
                    ${queryFragments.joinToString("\n")}
                }
            }
        """
}
