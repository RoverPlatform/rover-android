package io.rover.location.domain

/**
 * An emitted location position event.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val verticalAccuracy: Float?,
    val horizontalAccurancy: Float?
)
