package io.rover.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import io.rover.location.domain.Region
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.streams.subscribe

/**
 * Monitors for Geofence events using the Google Location Geofence API.
 *
 * Monitors the list of appropriate geofences to subscribe to as defined by the Rover API
 * via the [RegionObserver] interface.
 *
 * Google documentation: https://developer.android.com/training/location/geofencing.html
 */
class GoogleGeofenceService(
    private val applicationContext: Context,
    private val geofencingClient: GeofencingClient,
    private val locationReportingService: LocationReportingServiceInterface,
    private val permissionsNotifier: PermissionsNotifierInterface
    // TODO: customizable geofence limit
): GoogleGeofenceServiceInterface {
    override fun newGoogleGeofenceEvent(geofencingEvent: GeofencingEvent) {
        // have to do processing here because we need to know what the regions are.
        if(!geofencingEvent.hasError()) {
            val regions = geofencingEvent.triggeringGeofences.map {
                val fence = geofencingEvent.triggeringGeofences.first()

                val region = currentFences.firstOrNull { it.identifier == fence.requestId }

                if(region == null) {
                    val verb = when(geofencingEvent.geofenceTransition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                        Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                        else -> "unknown (${geofencingEvent.geofenceTransition})"
                    }
                    log.w("Received an $verb event for Geofence with request-id/identifier '${fence.requestId}', but not currently tracking that one. Ignoring.")
                }
                region
            }.filterNotNull()

            regions.forEach { region ->
                when (geofencingEvent.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        locationReportingService.trackEnterGeofence(
                            region
                        )
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        locationReportingService.trackExitGeofence(
                            region
                        )
                    }
                }
            }
        } else {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(
                geofencingEvent.errorCode
            )

            log.w("Unable to capture Geofence message because: $errorMessage")
        }
    }

    override fun regionsUpdated(regions: List<Region>) {
        currentFences = regions.filterIsInstance(Region.GeofenceRegion::class.java)

        startMonitoringGeofencesIfPossible()
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoringGeofencesIfPossible() {
        if(permissionObtained && currentFences.isNotEmpty()) {
            log.v("Updating geofences.")
            // This will remove any existing Rover geofences, because will all be registered with the
            // same pending intent pointing to the receiver intent service.
            geofencingClient.removeGeofences(
                pendingIntentForReceiverService()
            )

            val geofences = currentFences.map { region ->
                Geofence.Builder()
                    .setRequestId(region.identifier)
                    .setCircularRegion(
                        region.latitude,
                        region.longitude,
                        region.radius.toFloat()
                    )
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .addGeofences(geofences)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .build()

            geofencingClient.addGeofences(request, pendingIntentForReceiverService()).addOnFailureListener { error ->
                log.w("Unable to configure Rover Geofence receiver because: $error")
            }.addOnSuccessListener {
                log.v("Now monitoring ${geofences.count()} Rover geofences.")
            }

        }
    }

    /**
     * A Pending Intent for activating the receiver service, [GeofenceBroadcastReceiver].
     *
     * The
     */
    private fun pendingIntentForReceiverService(): PendingIntent {
        return PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(applicationContext,  GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * State: the current subset of fences we're monitoring for.
     */
    private var currentFences: List<Region.GeofenceRegion> = // listOf()
        listOf(
            // vistek
            Region.GeofenceRegion(
                43.656768,
                -79.3619847,
                300.0
            ),
            // home
            Region.GeofenceRegion(
                43.6857362,
                -79.4170394,
                300.0
            ),
            // Rover office
            Region.GeofenceRegion(
                43.6506783,
                -79.3780025,
                300.0
            )
        )

    /**
     * State: have we been granted permission to use location services yet?
     */
    private var permissionObtained = false

    init {
        permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).subscribe {
            permissionObtained = true
            startMonitoringGeofencesIfPossible()
        }
    }
}

class GeofenceBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Rover.sharedInstance.resolveSingletonOrFail(GoogleGeofenceServiceInterface::class.java).newGoogleGeofenceEvent(
            GeofencingEvent.fromIntent(intent)
        )
    }
}
