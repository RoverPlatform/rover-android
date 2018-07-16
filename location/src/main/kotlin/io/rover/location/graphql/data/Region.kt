package io.rover.location.graphql.data

import io.rover.location.domain.Region
import io.rover.core.data.graphql.optIntOrNull
import io.rover.core.data.graphql.putProp
import io.rover.core.data.graphql.safeGetString
import org.json.JSONObject
import java.util.UUID


val Region.BeaconRegion.Companion.resourceName get() = "BeaconRegion"
val Region.GeofenceRegion.Companion.resourceName get() = "GeofenceRegion"

internal fun Region.Companion.decodeJson(json: JSONObject): Region {
    val typeName = json.safeGetString("__typename")

    return when (typeName) {
        Region.BeaconRegion.resourceName -> Region.BeaconRegion(
            uuid = UUID.fromString(json.safeGetString("uuid")),
            major = json.optIntOrNull("major")?.toShort(),
            minor = json.optIntOrNull("minor")?.toShort()
        )
        Region.GeofenceRegion.resourceName -> Region.GeofenceRegion(
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            radius = json.getDouble("radius")
        )
        else -> throw RuntimeException("Unsupported Region type '$typeName'")
    }
}

internal fun Region.encodeJson(): JSONObject {
    return JSONObject().apply {
        put("__typename", when (this@encodeJson) {
            is Region.BeaconRegion -> {
                putProp(this@encodeJson, Region.BeaconRegion::uuid) { it.toString().toUpperCase() }
                putProp(this@encodeJson, Region.BeaconRegion::major) { it ?: JSONObject.NULL }
                putProp(this@encodeJson, Region.BeaconRegion::minor) { it ?: JSONObject.NULL }
                Region.BeaconRegion.resourceName
            }
            is Region.GeofenceRegion -> {
                putProp(this@encodeJson, Region.GeofenceRegion::longitude)
                putProp(this@encodeJson, Region.GeofenceRegion::latitude)
                putProp(this@encodeJson, Region.GeofenceRegion::radius)
                Region.GeofenceRegion.resourceName
            }
        })
    }
}
