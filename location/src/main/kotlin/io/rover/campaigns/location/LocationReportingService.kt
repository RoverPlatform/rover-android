package io.rover.campaigns.location

import io.rover.campaigns.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.domain.Event
import io.rover.campaigns.location.domain.Beacon
import io.rover.campaigns.location.domain.Geofence
import io.rover.campaigns.core.data.domain.Location
import io.rover.campaigns.location.domain.events.asAttributeValue

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
