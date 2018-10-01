package io.rover.core.data.graphql.operations.data

import io.rover.core.data.domain.Location
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import io.rover.core.data.graphql.safeOptString
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.whenNotNull
import org.json.JSONArray
import org.json.JSONObject

fun Location.encodeJson(dateFormatting: DateFormattingInterface): JSONObject {
    return JSONObject().apply {
        listOf(
            Location::altitude, Location::horizontalAccuracy, Location::verticalAccuracy
        ).forEach { putProp(this@encodeJson, it) }

        putProp(this@encodeJson, Location::coordinate, "coordinate") {
            JSONArray(
                listOf(
                    coordinate.latitude, coordinate.longitude
                )
            )
        }

        putProp(this@encodeJson, Location::address) {
            it?.encodeJson() ?: JSONObject.NULL
        }

        putProp(this@encodeJson, Location::timestamp) { dateFormatting.dateAsIso8601(timestamp) }
    }
}

fun Location.Address.encodeJson(): JSONObject {
    return JSONObject().apply {
        listOf(
            Location.Address::street,
            Location.Address::city,
            Location.Address::state,
            Location.Address::postalCode,
            Location.Address::country,
            Location.Address::isoCountryCode,
            Location.Address::subAdministrativeArea,
            Location.Address::subLocality
        ).forEach { putProp(this@encodeJson, it) }
    }
}

fun Location.Companion.decodeJson(
    json: JSONObject,
    dateFormatting: DateFormattingInterface
): Location {
    val coordinateArray = json.getJSONArray("coordinate")
    return Location(
        Location.Coordinate(
            coordinateArray.getDouble(0),
            coordinateArray.getDouble(1)
        ),
        json.getDouble("altitude"),
        json.getDouble("verticalAccuracy"),
        json.getDouble("horizontalAccuracy"),
        dateFormatting.iso8601AsDate(json.safeGetString("timestamp")),
        json.optJSONObject("address").whenNotNull {  Location.Address.decodeJson(it) }
    )
}

fun Location.Address.Companion.decodeJson(json: JSONObject): Location.Address {
    return Location.Address(
        street = json.safeOptString("street"),
        city = json.safeOptString("city"),
        state = json.safeOptString("state"),
        postalCode = json.safeOptString("postalCode"),
        country = json.safeOptString("country"),
        isoCountryCode = json.safeOptString("isoCountryCode"),
        subAdministrativeArea = json.safeOptString("subAdministrativeArea"),
        subLocality = json.safeOptString("subLocality")
    )
}
