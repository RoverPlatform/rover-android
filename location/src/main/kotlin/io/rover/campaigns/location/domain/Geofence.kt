package io.rover.campaigns.location.domain

import io.rover.campaigns.core.data.domain.ID
import io.rover.campaigns.core.data.domain.Location
import java.util.Date

data class Geofence(
    val center: Center,
    val id: ID,
    val name: String,
    val radius: Double,
    val tags: List<String>
) {
    data class Center(
        val latitude: Double,
        val longitude: Double
    )

    val identifier: String
        get() = "${center.latitude}:${center.longitude}:$radius"

    data class IdentiferComponents(
        val latitude: Double,
        val longitude: Double,
        val radius: Double
    ) {
        constructor(components: List<String>) : this(
            components[0].toDouble(),
            components[1].toDouble(),
            components[2].toDouble()
        )

        constructor(identifier: String) : this(identifier.split(":"))
    }

    companion object
}

fun Geofence.Center.asLocation(): Location {
    return Location(
        Location.Coordinate(
            latitude, longitude
        ),
        0.0, -1.0, -1.0,
        Date(), null
    )
}
