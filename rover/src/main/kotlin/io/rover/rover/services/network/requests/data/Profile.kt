package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Profile
import io.rover.rover.services.network.putProp
import org.json.JSONObject
import java.net.URI
import java.net.URL

fun Profile.Companion.decodeJson(jsonObject: JSONObject): Profile {
    return Profile(
        identifier = jsonObject.optString("identifier"),
        attributes = jsonObject.getJSONObject("attributes").toFlatAttributesHash()
    )
}

fun Profile.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Profile::attributes) { it.encodeJson() }
        putProp(this@encodeJson, Profile::identifier)
    }
}
