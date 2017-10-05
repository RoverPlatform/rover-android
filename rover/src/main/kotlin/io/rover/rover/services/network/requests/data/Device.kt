package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Device
import io.rover.rover.core.domain.Profile
import io.rover.rover.core.domain.Region
import io.rover.rover.services.network.putProp
import org.json.JSONArray
import org.json.JSONObject

fun Device.Companion.decodeJson(jsonObject: JSONObject): Device {
    return Device(
        profile = Profile.decodeJson(jsonObject.getJSONObject("profile")),
        regions = jsonObject.getJSONArray("regions").getObjectIterable().map {
            Region.decodeJson(it)
        }.toSet()
    )
}

fun Device.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Device::profile) { it.encodeJson() }
        putProp(this@encodeJson, Device::regions) { JSONArray(it.map { it.encodeJson() } ) }
    }
}
