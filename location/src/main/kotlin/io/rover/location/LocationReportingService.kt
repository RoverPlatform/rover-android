package io.rover.location

import io.rover.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.location.domain.Location
import io.rover.location.domain.Region
import io.rover.location.domain.events.asAttributeValue

class LocationReportingService(
    private val eventQueueService: EventQueueServiceInterface
) : LocationReportingServiceInterface {
    override fun trackEnterGeofence(geofence: Region.GeofenceRegion) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Region Entered",
                hashMapOf(
                    Pair("region", geofence.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackExitGeofence(geofence: Region.GeofenceRegion) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Region Exited",
                hashMapOf(
                    Pair("region", geofence.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackEnterBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Entered",
                hashMapOf(
                    Pair("region", beaconRegion.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun trackExitBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Exited",
                hashMapOf(
                    Pair("region", beaconRegion.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }

    override fun updateLocation(location: Location) {
        eventQueueService.trackEvent(
            Event(
                "Location Updated",
                hashMapOf(
                    Pair("location", location.asAttributeValue())
                )
            ),
            ROVER_NAMESPACE
        )
    }
}
