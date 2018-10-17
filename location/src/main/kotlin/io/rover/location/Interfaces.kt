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

    @Deprecated("Use enclosingGeofences instead.")
    val currentGeofences: List<Geofence>

    /**
     * Returns a list of geofences that the device is currently physically enclosed by.
     *
     * Note that this works by monitoring the geofence enter/exit events emitted by Google, and only
     * stored in-memory.  Thus this is best-effort only: the list of enclosing geofences
     * will be cleared in the case of the app being stopped in the background by Android.
     *
     * That means that this may give you false negatives (but not false positives): a geofence
     * the user is enclosed by may not be present in this list at a given moment.
     */
    val enclosingGeofences: List<Geofence>

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
