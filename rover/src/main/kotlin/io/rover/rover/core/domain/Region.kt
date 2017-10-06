package io.rover.rover.core.domain

import java.util.*

sealed class Region {
    data class BeaconRegion(
        val uuid: UUID,
        val major: Int?,
        val minor: Int?
    ): Region() {
        companion object
    }

    data class GeofenceRegion(
        val latitude: Double,
        val longitude: Double,
        val radius: Double
    ): Region() {
        companion object
    }

    companion object
}
