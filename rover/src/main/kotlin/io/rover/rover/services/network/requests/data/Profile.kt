package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Profile
import org.json.JSONObject

fun Profile.Companion.decodeJson(jsonObject: JSONObject): Profile {
    return Profile(
        identifier = jsonObject.optString("identifier"),
        attributes = jsonObject.getJSONObject("attributes").toFlatAttributesHash()
    )
}

fun JSONObject.toFlatAttributesHash(): Map<String, String> {
    return this.keys().asSequence().map { key ->
        Pair(key, this.getString(key))
    }.associate { it }
}
