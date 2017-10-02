package io.rover.rover.services.network.requests.data

import io.rover.rover.core.domain.Region
import org.json.JSONObject
import java.util.*

val Region.BeaconRegion.Companion.resourceName get() = "BeaconRegion"
val Region.GeofenceRegion.Companion.resourceName get() = "GeofenceRegion"

fun Region.Companion.decodeJson(json: JSONObject): Region {
    val typeName = json.getString("__typename")

    return when(typeName) {
        Region.BeaconRegion.resourceName -> Region.BeaconRegion(
            uuid = UUID.fromString(json.getString("uuid")),
            major = json.getInt("major"),
            minor = json.getInt("minor")
        )
        Region.GeofenceRegion.resourceName ->  Region.GeofenceRegion(
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            radius = json.getDouble("radius")
        )
        else -> throw RuntimeException("Unsupported Region type '$typeName'")
    }
}
