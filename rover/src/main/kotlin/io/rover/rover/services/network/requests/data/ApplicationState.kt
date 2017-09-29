package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.ApplicationState
import io.rover.rover.core.domain.Profile
import io.rover.rover.core.domain.Region
import org.json.JSONObject

fun ApplicationState.Companion.decodeJson(jsonObject: JSONObject): ApplicationState {
    return ApplicationState(
        profile = Profile.decodeJson(jsonObject.getJSONObject("profile")),
        regions = jsonObject.getJSONArray("regions").getObjectIterable().map {
            Region.decodeJson(it)
        }.toSet()
    )
}
