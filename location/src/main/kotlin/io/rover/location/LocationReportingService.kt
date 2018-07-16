package io.rover.location

import io.rover.location.domain.Region
import io.rover.core.data.domain.AttributeValue
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event

class LocationReportingService(
    private val eventQueueService: EventQueueServiceInterface
): LocationReportingServiceInterface {
    override fun trackEnterGeofence(geofence: Region.GeofenceRegion) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Region Entered",
                hashMapOf(
                    Pair("identifier", AttributeValue.String(geofence.identifier)),
                    Pair("latitude", AttributeValue.Double(geofence.latitude)),
                    Pair("longitude", AttributeValue.Double(geofence.longitude)),
                    Pair("radius", AttributeValue.Double(geofence.radius))
                )
            )
        )
    }

    override fun trackExitGeofence(geofence: Region.GeofenceRegion) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Region Exited",
                hashMapOf(
                    Pair("identifier", AttributeValue.String(geofence.identifier)),
                    Pair("latitude", AttributeValue.Double(geofence.latitude)),
                    Pair("longitude", AttributeValue.Double(geofence.longitude)),
                    Pair("radius", AttributeValue.Double(geofence.radius))
                )
            )
        )
    }

    override fun trackEnterBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Entered",
                hashMapOf(
                    Pair("identifier", AttributeValue.String(beaconRegion.identifier)),
                    Pair("uuid", AttributeValue.String(beaconRegion.uuid.toString())),
                    Pair("major", AttributeValue.String(beaconRegion.major.toString())),
                    Pair("minor", AttributeValue.String(beaconRegion.minor.toString()))
                )
            )
        )
    }

    override fun trackExitBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Exited",
                hashMapOf(
                    Pair("identifier", AttributeValue.String(beaconRegion.identifier)),
                    Pair("uuid", AttributeValue.String(beaconRegion.uuid.toString())),
                    Pair("major", AttributeValue.String(beaconRegion.major.toString())),
                    Pair("minor", AttributeValue.String(beaconRegion.minor.toString()))
                )
            )
        )
    }

    override fun updateLocation(location: LocationReportingServiceInterface.Location) {
        eventQueueService.trackEvent(
            Event(
                "Location Updated",
                hashMapOf(
                    Pair("latitude", AttributeValue.Double(location.latitude)),
                    Pair("longitude", AttributeValue.Double(location.longitude)),
                    Pair("altitude", AttributeValue.Double(location.altitude))
                ) + (if(location.horizontalAccurancy != null) {
                    hashMapOf(Pair("horizontalAccuracy", AttributeValue.Double(location.horizontalAccurancy.toDouble())))
                } else hashMapOf()) + if(location.verticalAccuracy != null) {
                    hashMapOf(Pair("verticalAccuracy", AttributeValue.Double(location.verticalAccuracy.toDouble())))
                } else hashMapOf()
            )
        )
    }
}
