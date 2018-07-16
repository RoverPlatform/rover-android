package io.rover.location

import android.content.Intent
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult
import io.rover.location.domain.Region

interface GoogleBackgroundLocationServiceInterface {
    /**
     * The Google Location Services have yielded a new [LocationResult] to us.
     */
    fun newGoogleLocationResult(locationResult: LocationResult)
}

interface GoogleBeaconTrackerServiceInterface: RegionObserver {
    fun newGoogleBeaconMessage(intent: Intent)
}

/**
 * Implementers can register themselves with [RegionRepositoryInterface] to be informed
 * of the most recent list of Geofences that should be monitored.
 */
interface GoogleGeofenceServiceInterface: RegionObserver {
    fun newGoogleGeofenceEvent(geofencingEvent: GeofencingEvent)
}

interface RegionRepositoryInterface {
    /**
     * Register an object that should be updated whenever the regions are updated.
     */
    fun registerObserver(regionObserver: RegionObserver)
}

interface RegionObserver {
    /**
     * Called when the Rover regions change.
     */
    fun regionsUpdated(regions: List<Region>)
}

/**
 * Dispatch location events to Rover, pertaining to location updates, geofences, and beacons.
 */
interface LocationReportingServiceInterface {
    fun trackEnterGeofence(geofence: Region.GeofenceRegion)

    fun trackExitGeofence(geofence: Region.GeofenceRegion)

    fun trackEnterBeacon(beaconRegion: Region.BeaconRegion)

    fun trackExitBeacon(beaconRegion: Region.BeaconRegion)

    fun updateLocation(
        location: Location
    )

    data class Location(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val verticalAccuracy: Float?,
        val horizontalAccurancy: Float?
    )
}
