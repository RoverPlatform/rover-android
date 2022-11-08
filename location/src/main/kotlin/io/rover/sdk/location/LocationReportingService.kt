package io.rover.sdk.location

import io.rover.sdk.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.location.domain.Beacon
import io.rover.sdk.location.domain.Geofence
import io.rover.sdk.core.data.domain.Location
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
