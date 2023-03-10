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

package io.rover.sdk.location.domain

import io.rover.sdk.core.data.domain.ID
import io.rover.sdk.core.data.domain.Location
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
            latitude,
            longitude
        ),
        0.0,
        -1.0,
        -1.0,
        Date(),
        null
    )
}
