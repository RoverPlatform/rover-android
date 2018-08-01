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
                    Pair("identifier", AttributeValue.Scalar.String(geofence.identifier)),
                    Pair("latitude", AttributeValue.Scalar.Double(geofence.latitude)),
                    Pair("longitude", AttributeValue.Scalar.Double(geofence.longitude)),
                    Pair("radius", AttributeValue.Scalar.Double(geofence.radius))
                )
            )
        )
    }

    override fun trackExitGeofence(geofence: Region.GeofenceRegion) {
        eventQueueService.trackEvent(
            Event(
                "Geofence Region Exited",
                hashMapOf(
                    Pair("identifier", AttributeValue.Scalar.String(geofence.identifier)),
                    Pair("latitude", AttributeValue.Scalar.Double(geofence.latitude)),
                    Pair("longitude", AttributeValue.Scalar.Double(geofence.longitude)),
                    Pair("radius", AttributeValue.Scalar.Double(geofence.radius))
                )
            )
        )
    }

    override fun trackEnterBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Entered",
                hashMapOf(
                    Pair("identifier", AttributeValue.Scalar.String(beaconRegion.identifier)),
                    Pair("uuid", AttributeValue.Scalar.String(beaconRegion.uuid.toString())),
                    Pair("major", AttributeValue.Scalar.String(beaconRegion.major.toString())),
                    Pair("minor", AttributeValue.Scalar.String(beaconRegion.minor.toString()))
                )
            )
        )
    }

    override fun trackExitBeacon(beaconRegion: Region.BeaconRegion) {
        eventQueueService.trackEvent(
            Event(
                "Beacon Region Exited",
                hashMapOf(
                    Pair("identifier", AttributeValue.Scalar.String(beaconRegion.identifier)),
                    Pair("uuid", AttributeValue.Scalar.String(beaconRegion.uuid.toString())),
                    Pair("major", AttributeValue.Scalar.String(beaconRegion.major.toString())),
                    Pair("minor", AttributeValue.Scalar.String(beaconRegion.minor.toString()))
                )
            )
        )
    }

    override fun updateLocation(location: LocationReportingServiceInterface.Location) {
        eventQueueService.trackEvent(
            Event(
                "Location Updated",
                hashMapOf(
                    Pair("latitude", AttributeValue.Scalar.Double(location.latitude)),
                    Pair("longitude", AttributeValue.Scalar.Double(location.longitude)),
                    Pair("altitude", AttributeValue.Scalar.Double(location.altitude))
                ) + (if(location.horizontalAccurancy != null) {
                    hashMapOf(Pair("horizontalAccuracy", AttributeValue.Scalar.Double(location.horizontalAccurancy.toDouble())))
                } else hashMapOf()) + if(location.verticalAccuracy != null) {
                    hashMapOf(Pair("verticalAccuracy", AttributeValue.Scalar.Double(location.verticalAccuracy.toDouble())))
                } else hashMapOf()
            )
        )
    }
}
