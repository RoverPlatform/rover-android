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

package io.rover.sdk.location

import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.location.domain.Beacon
import io.rover.sdk.location.domain.Geofence
import io.rover.sdk.location.domain.events.asAttributeValue

class LocationReportingService(
    private val eventQueueService: EventQueueServiceInterface
) : LocationReportingServiceInterface {
    override fun trackEnterGeofence(geofence: Geofence) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Entered",
                hashMapOf(
                    Pair("geofence", geofence.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackExitGeofence(geofence: Geofence) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Exited",
                hashMapOf(
                    Pair("geofence", geofence.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackEnterBeacon(beacon: Beacon) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Entered",
                hashMapOf(
                    Pair("beacon", beacon.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackExitBeacon(beacon: Beacon) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Exited",
                hashMapOf(
                    Pair("beacon", beacon.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun updateLocation(location: Location) {
        eventQueueService.trackEvent(
            Event(
                "Location Updated",
                hashMapOf()
            ),
            ROVER_NAMESPACE
        )
    }
}
