package io.rover.location.domain

import java.util.UUID

sealed class Region {
    data class BeaconRegion(
        val uuid: UUID,
        val major: Short?,
        val minor: Short?
    ) : Region() {
        override val identifier: String
            get() = when {
                major != null && minor != null -> "$uuid:$major:$minor"
                major != null -> "$uuid:$major"
                else -> uuid.toString()
            }

        companion object
    }

    data class GeofenceRegion(
        val latitude: Double,
        val longitude: Double,

        /**
         * Given in meters.
         */
        val radius: Double
    ) : Region() {
        override val identifier: String
            get() = "$latitude:$longitude:$radius"

        companion object
    }

    abstract val identifier: String

    companion object
}
