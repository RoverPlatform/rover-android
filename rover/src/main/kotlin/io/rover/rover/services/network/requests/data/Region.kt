package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Region
import io.rover.rover.services.network.optIntOrNull
import io.rover.rover.services.network.putProp
import org.json.JSONObject
import java.util.*

val Region.BeaconRegion.Companion.resourceName get() = "BeaconRegion"
val Region.GeofenceRegion.Companion.resourceName get() = "GeofenceRegion"

fun Region.Companion.decodeJson(json: JSONObject): Region {
    val typeName = json.getString("__typename")

    return when (typeName) {
        Region.BeaconRegion.resourceName -> Region.BeaconRegion(
            uuid = UUID.fromString(json.getString("uuid")),
            major = json.optIntOrNull("major"),
            minor = json.optIntOrNull("minor")
        )
        Region.GeofenceRegion.resourceName -> Region.GeofenceRegion(
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            radius = json.getDouble("radius")
        )
        else -> throw RuntimeException("Unsupported Region type '$typeName'")
    }
}

fun Region.encodeJson(): JSONObject {
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
