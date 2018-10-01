package io.rover.core.data.domain

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

    companion object
}
