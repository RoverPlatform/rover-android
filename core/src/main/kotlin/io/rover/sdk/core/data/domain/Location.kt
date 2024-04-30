/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.data.domain

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
    ) {
        fun toMap(): Map<String, Any> {
            // NB. this format differs from what we sent as JSON in the event queue
            // (see Location.encodeJson): there, coordinate is encoded as an array/tuple
            // [lat, long]. Here, for better ergonomics for using the coordinates in an
            // experiences, it is encoded here as an object instead.
            return mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude)
        }
    }

    data class Address(
        val city: String?,
        val state: String?,
        val country: String?,
        val isoCountryCode: String?,
        val subAdministrativeArea: String?
    ) {
        companion object

        fun toMap(): Map<String, Any> {
            return mapOf(
                "city" to city,
                "state" to state,
                "country" to country,
                "isoCountryCode" to isoCountryCode,
                "subAdministrativeArea" to subAdministrativeArea,
            )
                    .filterValues { it != null }
                    .mapValues { it.value as Any }
        }
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

    fun toMap(): Map<String, Any> {
        return mapOf(
            "coordinate" to coordinate.toMap(),
            "altitude" to altitude,
            "verticalAccuracy" to verticalAccuracy,
            "horizontalAccuracy" to horizontalAccuracy,
            "timestamp" to timestamp.time,
            "address" to address?.toMap()
        )
                .filterValues { it != null }
                .mapValues { it.value as Any }
    }
}
