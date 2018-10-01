package io.rover.location

import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult
import io.rover.location.domain.Beacon
import io.rover.location.domain.Geofence
import io.rover.core.data.domain.Location
import org.reactivestreams.Publisher

interface GoogleBackgroundLocationServiceInterface {
    /**
     * The Google Location Services have yielded a new [LocationResult] to us.
     */
    fun newGoogleLocationResult(locationResult: LocationResult)

    /**
     * Be informed of location changes.
     */
    val locationUpdates: Publisher<Location>
}

interface GoogleBeaconTrackerServiceInterface {
    fun newGoogleBeaconMessage(intent: Intent)
}

interface GeofenceServiceInterface {
    /**
     * Subscribe to this publisher to be informed of geofence events.
     */
    val geofenceEvents: Publisher<GeofenceEvent>

    /**
     * Returns a list of geofences that the device is currently physically contained by.
     */
    val currentGeofences: List<Geofence>

    data class GeofenceEvent(
        val exit: Boolean,
        val geofence: Geofence
    )
}

/**
 * Implementers can register themselves with [RegionRepositoryInterface] to be informed
 * of the most recent list of Geofences that should be monitored.
 */
interface GoogleGeofenceServiceInterface: GeofenceServiceInterface {
    /**
     * This callback is used internally to deliver Google geofencing events delivered to the SDK
     * via an intent into the [GoogleGeofenceServiceInterface] itself.
     */
    fun newGoogleGeofenceEvent(geofencingEvent: GeofencingEvent)
}

/**
 * Dispatch location events to Rover, pertaining to location updates, geofences, and beacons.
 */
interface LocationReportingServiceInterface {
    fun trackEnterGeofence(geofence: Geofence)

    fun trackExitGeofence(geofence: Geofence)

    fun trackEnterBeacon(beacon: Beacon)

    fun trackExitBeacon(beacon: Beacon)

    fun updateLocation(
        location: Location
    )
}
