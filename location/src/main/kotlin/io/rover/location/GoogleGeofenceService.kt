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
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.doOnNext
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.subscribe
import io.rover.core.data.domain.Location
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.share
import io.rover.location.domain.asLocation
import io.rover.location.sync.GeofencesRepository
import org.reactivestreams.Publisher

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
    mainScheduler: Scheduler,
    ioScheduler: Scheduler,
    private val locationReportingService: LocationReportingServiceInterface,
    permissionsNotifier: PermissionsNotifierInterface,
    geofencesRepository: GeofencesRepository,
    googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface,
    private val geofenceMonitorLimit: Int = 50
    // TODO: customizable geofence limit
) : GoogleGeofenceServiceInterface {
    private val geofenceSubject = PublishSubject<GeofenceServiceInterface.GeofenceEvent>()
    override val geofenceEvents: Publisher<GeofenceServiceInterface.GeofenceEvent> = geofenceSubject
        .observeOn(mainScheduler)
        .share()

    override val currentGeofences: MutableList<io.rover.location.domain.Geofence> = mutableListOf()

    override fun newGoogleGeofenceEvent(geofencingEvent: GeofencingEvent) {
        // have to do processing here because we need to know what the regions are.
        if (!geofencingEvent.hasError()) {
            val transitioningGeofences = geofencingEvent.triggeringGeofences.mapNotNull {
                val fence = geofencingEvent.triggeringGeofences.first()

                val region = currentlyMonitoredFences.firstOrNull { it.identifier == fence.requestId }

                if (region == null) {
                    val verb = when (geofencingEvent.geofenceTransition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                        Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                        else -> "unknown (${geofencingEvent.geofenceTransition})"
                    }
                    log.w("Received an $verb event for Geofence with request-id/identifier '${fence.requestId}', but not currently tracking that one. Ignoring.")
                }
                region
            }

            transitioningGeofences.forEach { geofence ->
                when (geofencingEvent.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        locationReportingService.trackEnterGeofence(
                            geofence
                        )
                        currentGeofences.add(geofence)

                        geofenceSubject.onNext(
                            GeofenceServiceInterface.GeofenceEvent(
                                false,
                                geofence
                            )
                        )

                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        locationReportingService.trackExitGeofence(
                            geofence
                        )

                        currentGeofences.remove(geofence)

                        geofenceSubject.onNext(
                            GeofenceServiceInterface.GeofenceEvent(
                                true,
                                geofence
                            )
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

    @SuppressLint("MissingPermission")
    private fun startMonitoringGeofences() {
        log.v("Updating geofences.")
        // This will remove any existing Rover geofences, because will all be registered with the
        // same pending intent pointing to the receiver intent service.
        geofencingClient.removeGeofences(
            pendingIntentForReceiverService()
        )

        if(currentlyMonitoredFences.isNotEmpty()) {
            val geofences = currentlyMonitoredFences.map { geofence ->
                Geofence.Builder()
                    .setRequestId(geofence.identifier)
                    .setCircularRegion(
                        geofence.center.latitude,
                        geofence.center.longitude,
                        geofence.radius.toFloat()
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
        } else {
            log.v("No geofences, so not setting up monitoring.")
        }
    }

    /**
     * A Pending Intent for activating the receiver service, [GeofenceBroadcastReceiver].
     */
    private fun pendingIntentForReceiverService(): PendingIntent {
        return PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(applicationContext, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * State: the current subset of fences we're monitoring for.
     */
    private var currentlyMonitoredFences: List<io.rover.location.domain.Geofence> = listOf()

    init {
        Publishers.combineLatest(
            permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).doOnNext { log.v("Permission obtained.") },
            geofencesRepository.allGeofences().doOnNext { log.v("Full geofences list obtained from sync.") },
            googleBackgroundLocationService.locationUpdates.doOnNext { log.v("Location update obtained so that distant geofences can be filtered out.") }
        ) { permission, fences, location ->
            Triple(permission, fences, location)
        }.observeOn(ioScheduler).map { (_, fences, location) ->
            log.v("Determining $geofenceMonitorLimit closest geofences for monitoring.")
            fences.use { it.sortedBy { it.center.asLocation().distanceTo(location) }.take(geofenceMonitorLimit).toList() }
        }.observeOn(mainScheduler).subscribe { fences ->
            log.v("Got location permission, geofences, and current location.  Ready to start monitoring.")
            currentlyMonitoredFences = fences
            startMonitoringGeofences()
        }
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Rover.sharedInstance.googleGeofenceService.newGoogleGeofenceEvent(
            GeofencingEvent.fromIntent(intent)
        )
    }
}

/**
 * Returns distance in meters.
 *
 * Assumes coordinates on Planet Earth.
 */
fun Location.distanceTo(other: Location): Double {
    val earthRadius = 6371000

    val lat1 = degreesToRadians(coordinate.latitude)
    val lon1 = degreesToRadians(coordinate.longitude)
    val lat2 = degreesToRadians(other.coordinate.latitude)
    val lon2 = degreesToRadians(other.coordinate.longitude)

    return earthRadius * ahaversine(
        haversine(lat2 - lat1) + Math.cos(lat1) * Math.cos(lat2) * haversine(lon2 - lon1)
    )
}

private fun haversine(value: Double): Double {
    return (1 - Math.cos(value)) / 2
}


private fun ahaversine(value: Double): Double {
    return 2 * Math.asin(Math.sqrt(value))
}


private fun degreesToRadians(degrees: Double): Double {
    return (degrees / 360.0) * 2 * Math.PI
}
