package io.rover.identity.graphql.data

import io.rover.identity.data.domain.Profile
import io.rover.core.data.graphql.operations.data.encodeJson
import io.rover.core.data.graphql.operations.data.toAttributesHash
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeOptString
import io.rover.core.platform.DateFormattingInterface
import org.json.JSONObject

internal fun Profile.Companion.decodeJson(jsonObject: JSONObject): Profile {
    return Profile(
        identifier = jsonObject.safeOptString("identifier"),
        attributes = jsonObject.getJSONObject("attributes").toAttributesHash()
    )
}

internal fun Profile.encodeJson(dateFormatting: DateFormattingInterface): JSONObject {
    return JSONObject().apply {
        putProp(this@encodeJson, Profile::attributes) { it.encodeJson(dateFormatting) }
        putProp(this@encodeJson, Profile::identifier)
    }
}
