package io.rover.identity.graphql.data

import io.rover.identity.data.domain.Profile
import io.rover.core.data.graphql.operations.data.encodeJson
import io.rover.core.data.graphql.operations.data.toFlatAttributesHash
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeOptString
import org.json.JSONObject

internal fun Profile.Companion.decodeJson(jsonObject: JSONObject): Profile {
    return Profile(
        identifier = jsonObject.safeOptString("identifier"),
        attributes = jsonObject.getJSONObject("attributes").toFlatAttributesHash()
    )
}

internal fun Profile.encodeJson(): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Profile::attributes) { it.encodeJson() }
        putProp(this@encodeJson, Profile::identifier)
    }
}
