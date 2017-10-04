package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Device
import io.rover.rover.core.domain.Profile
import io.rover.rover.core.domain.Region
import org.json.JSONObject

fun Device.Companion.decodeJson(jsonObject: JSONObject): Device {
    return Device(
        profile = Profile.decodeJson(jsonObject.getJSONObject("profile")),
        regions = jsonObject.getJSONArray("regions").getObjectIterable().map {
            Region.decodeJson(it)
        }.toSet()
    )
}
