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
