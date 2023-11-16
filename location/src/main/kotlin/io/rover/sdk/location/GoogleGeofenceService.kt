/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.data.graphql.getStringIterable
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.permissions.PermissionsNotifierInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.blockForResult
import io.rover.sdk.core.streams.doOnNext
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.share
import io.rover.sdk.core.streams.subscribe
import io.rover.sdk.location.domain.asLocation
import io.rover.sdk.location.sync.ClosableSequence
import io.rover.sdk.location.sync.GeofencesRepository
import kotlinx.coroutines.reactive.asPublisher
import org.json.JSONArray
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
    privacyService: PrivacyService,
    private val localStorage: LocalStorage,
    private val geofencingClient: GeofencingClient,
    mainScheduler: Scheduler,
    ioScheduler: Scheduler,
    private val locationReportingService: LocationReportingServiceInterface,
    permissionsNotifier: PermissionsNotifierInterface,
    private val geofencesRepository: GeofencesRepository,
    googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface,
    private val geofenceMonitorLimit: Int = 50
) : GoogleGeofenceServiceInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private val geofenceSubject = PublishSubject<GeofenceServiceInterface.GeofenceEvent>()
    override val geofenceEvents: Publisher<GeofenceServiceInterface.GeofenceEvent> = geofenceSubject
        .observeOn(mainScheduler)
        .share()

    @Deprecated("Use enclosingGeofences instead.")
    override val currentGeofences: List<io.rover.sdk.location.domain.Geofence>
        get() = enclosingGeofences

    override val enclosingGeofences: MutableList<io.rover.sdk.location.domain.Geofence> = mutableListOf()

    override fun newGoogleGeofenceEvent(geofencingEvent: GeofencingEvent) {
        // have to do processing here because we need to know what the regions are.
        if (!geofencingEvent.hasError()) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences
            if (triggeringGeofences == null) {
                log.w("Unable to obtain list of geofences that triggered a GeofencingEvent from Google.")
                return
            }
            val transitioningGeofences = triggeringGeofences.mapNotNull { fence ->
                val geofence = geofencesRepository.geofenceByIdentifier(
                    fence.requestId
                ).blockForResult().firstOrNull()

                if (geofence == null) {
                    val verb = when (geofencingEvent.geofenceTransition) {
                        Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                        Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                        else -> "unknown (${geofencingEvent.geofenceTransition})"
                    }
                    log.w("Received an $verb event for Geofence with request-id/identifier '${fence.requestId}', but not currently tracking that one. Ignoring.")
                }

                geofence
            }

            transitioningGeofences.forEach { geofence ->
                when (geofencingEvent.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        locationReportingService.trackEnterGeofence(
                            geofence
                        )
                        enclosingGeofences.add(geofence)

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

                        enclosingGeofences.remove(geofence)

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
    private fun startMonitoringGeofences(updatedFencesList: List<io.rover.sdk.location.domain.Geofence>) {
        log.v("Updating geofences.")
        // the fences we ultimately want to be monitoring once the following operation is complete.
        val targetFenceIds = updatedFencesList.map { it.identifier }.toSet()

        val alreadyInGoogle = if (activeFences == null || activeFences?.isEmpty() == true) {
            // if I don't have any persisted "previously set" fences, then I should do a full
            // clear from our pending intent, because I don't know what fences could be left from a
            // prior SDK that wasn't tracking the state.
            geofencingClient.removeGeofences(
                pendingIntentForReceiverService()
            )
            emptySet()
        } else {
            // remove any geofences that are active but no longer in our list of target geofence ids.
            val staleGeofences = (activeFences!! - targetFenceIds).toList()
            if (staleGeofences.isNotEmpty()) {
                geofencingClient.removeGeofences(
                    staleGeofences
                )
            } else {
                log.v("No removals from currently monitored geofences on Google needed.")
            }
            // now google's state is that it has all target fences that were ALREADY being monitored.
            activeFences!!.intersect(targetFenceIds)
        }

        // TODO: if tracking disabled, instead just clear all these geofences

        val toAdd = targetFenceIds - alreadyInGoogle

        val fencesByIdentifier = updatedFencesList.associateBy { it.identifier }

        val geofences = toAdd.map { geofenceIdentifier ->
            val geofence = fencesByIdentifier[geofenceIdentifier] ?: throw RuntimeException(
                "Logic error in Rover's GoogleGeofenceService, identifier missing in indexed geofences mapping."
            )

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

        if (geofences.isNotEmpty()) {
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
            log.v("No additions to currently monitored geofences on Google needed.")
        }

        activeFences = targetFenceIds
    }

    @SuppressLint("MissingPermission")
    private fun stopMonitoring() {
        log.v("Stopping monitoring of geofences.")
        geofencingClient.removeGeofences(
            pendingIntentForReceiverService()
        )
        activeFences = emptySet()
    }

    /**
     * A Pending Intent for activating the receiver service, [GeofenceBroadcastReceiver].
     */
    private fun pendingIntentForReceiverService(): PendingIntent {
        return PendingIntent.getBroadcast(
            applicationContext,
            0,
            Intent(applicationContext, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { FLAG_IMMUTABLE } else { 0 }
        )
    }

    /**
     * A list of the IDs of all the Geofences that have already been pushed into Google, by
     * requestId.
     */
    private var activeFences: Set<String>? =
        try {
            val currentFencesJson = store[ACTIVE_FENCES_KEY]
            when (currentFencesJson) {
                null -> null
                else -> JSONArray(currentFencesJson).getStringIterable().toSet()
            }
        } catch (throwable: Throwable) {
            log.w("Corrupted list of active geofences, ignoring and starting fresh.  Cause: ${throwable.message}")
            null
        }
        set(value) {
            field = value
            store[ACTIVE_FENCES_KEY] = value.whenNotNull { JSONArray(it.toList()).toString() }
        }

    init {
        // wait for all pre-requisites to become available before starting to monitor for geofences.
        Publishers.combineLatest(
            // observeOn(mainScheduler) used on each because combineLatest() is not thread safe.

            // This publisher doesn't yield until permission is granted, so we don't need
            // to actually check its value further down the chain.
            permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).observeOn(mainScheduler).doOnNext { log.v("Permission obtained.") },
            geofencesRepository.allGeofences().observeOn(mainScheduler).doOnNext { log.v("Full geofences list obtained from sync.") },
            googleBackgroundLocationService.locationUpdates.observeOn(mainScheduler).doOnNext { log.v("Location update obtained so that distant geofences can be filtered out.") },
            privacyService.trackingModeFlow.asPublisher().doOnNext() { log.v("Informed of tracking mode: $it") },

        ) { _, fences, location, trackingMode ->
            if (trackingMode == PrivacyService.TrackingMode.Default) {
                Command.Enable(fences, location)
            } else {
                Command.Disable
            }
        }.observeOn(ioScheduler).map { command: Command ->
            when (command) {
                is Command.Enable -> {
                    val (fences, location) = command
                    log.v("Determining $geofenceMonitorLimit closest geofences for monitoring.")
                    fences.iterator().use { it.asSequence().sortedBy { it.center.asLocation().distanceTo(location) }.take(geofenceMonitorLimit).toList() }
                }
                is Command.Disable -> {
                    log.v("Geofences are now disabled, monitoring for 0 geofences.")
                    emptyList()
                }
            }
        }.observeOn(mainScheduler).subscribe { fences ->
            if (fences.isEmpty()) {
                stopMonitoring()
            } else {
                log.v("Got location permission, privacy setting, geofences, and current location.  Ready to start monitoring for ${fences.count()} geofence(s).")
                startMonitoringGeofences(fences)
            }
        }
    }

    /**
     * Describes how the the geofence service should configure itself.
     */
    private sealed class Command {
        data class Enable(
            val fences: ClosableSequence<io.rover.sdk.location.domain.Geofence>,
            val location: Location,
        ) : Command()

        object Disable : Command()
    }

    companion object {
        private const val ACTIVE_FENCES_KEY = "active-fences"
        private const val STORAGE_CONTEXT_IDENTIFIER = "google-geofence-service"
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rover = Rover.failableShared
        if (rover == null) {
            log.e("Received a geofence result from Google, but Rover is not initialized.  Ignoring.")
            return
        }
        val geofenceService = rover.resolve(GoogleGeofenceServiceInterface::class.java)
        if (geofenceService == null) {
            log.e("Received a geofence result from Google, but GoogleGeofenceServiceInterface is not registered in the Rover container. Ensure LocationAssembler() is in Rover.initialize(). Ignoring.")
            return
        }
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            log.w("Unable to hydrate a GeofencingEvent from an incoming broadcast receive intent.")
            return
        }
        geofenceService.newGoogleGeofenceEvent(
            geofencingEvent
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
