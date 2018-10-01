package io.rover.location

import io.rover.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.location.domain.Beacon
import io.rover.location.domain.Geofence
import io.rover.core.data.domain.Location
import io.rover.location.domain.events.asAttributeValue

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
