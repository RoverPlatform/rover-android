package io.rover.campaigns.core.data.domain

import java.util.Date

/**
 * An emitted location position event.
 */
data class Location(
    val coordinate: Coordinate,
    val altitude: Double,
    val verticalAccuracy: Double,
    val horizontalAccuracy: Double,
    val timestamp: Date,
    val address: Address?
) {
    data class Coordinate(
        val latitude: Double,
        val longitude: Double
    )

    data class Address(
        val street: String?,
        val city: String?,
        val state: String?,
        val postalCode: String?,
        val country: String?,
        val isoCountryCode: String?,
        val subAdministrativeArea: String?,
        val subLocality: String?
    ) {
        companion object
    }

    fun isSignificantDisplacement(location: Location): Boolean {
        val loc1 = android.location.Location("").apply {
            latitude = coordinate.latitude
            longitude = coordinate.longitude
        }

        val loc2 = android.location.Location("").apply {
            latitude = location.coordinate.latitude
            longitude = location.coordinate.longitude
        }

        return loc1.distanceTo(loc2) > MINIMUM_DISPLACEMENT_DISTANCE
    }

    companion object {
        /**
         * The minimum displacement distance in meters considered significant displacement.
         */
        const val MINIMUM_DISPLACEMENT_DISTANCE = 500f
    }
}
