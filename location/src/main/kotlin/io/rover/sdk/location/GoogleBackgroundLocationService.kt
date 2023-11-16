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
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.rover.sdk.core.Rover
import io.rover.sdk.core.data.domain.Location
import io.rover.sdk.core.data.domain.Location.Companion.MINIMUM_DISPLACEMENT_DISTANCE
import io.rover.sdk.core.data.graphql.operations.data.decodeJson
import io.rover.sdk.core.data.graphql.operations.data.encodeJson
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.permissions.PermissionsNotifierInterface
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.Publishers
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.filterNulls
import io.rover.sdk.core.streams.map
import io.rover.sdk.core.streams.observeOn
import io.rover.sdk.core.streams.share
import io.rover.sdk.core.streams.shareAndReplay
import io.rover.sdk.core.streams.subscribe
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

/**
 * Subscribes to Location Updates from FusedLocationManager and emits location reporting events.
 *
 * Despite the name, this object is responsible for both background location monitoring
 * and foreground location monitoring. Moreover, background location monitoring is no longer
 * supported as of Android 12.
 *
 * This will allow you to see up to date location data for your users in the Rover Audience app if
 * [trackLocation] is enabled.
 *
 * Google documentation: https://developer.android.com/training/location/receive-location-updates.html
 */
class GoogleBackgroundLocationService(
    private val privacyService: PrivacyService,
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val applicationContext: Context,
    private val permissionsNotifier: PermissionsNotifierInterface,
    private val locationReportingService: LocationReportingServiceInterface,
    private val geocoder: Geocoder,
    ioScheduler: Scheduler,
    mainScheduler: Scheduler,
    private val trackLocation: Boolean = false,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface,
) : GoogleBackgroundLocationServiceInterface, PrivacyService.TrackingEnabledChangedListener {

    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override fun newGoogleLocationResult(locationResult: LocationResult) {
        if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return
        log.v("Received location result: $locationResult")
        subject.onNext(locationResult)
    }

    private val subject = PublishSubject<LocationResult>()

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "last-known-location"
        private const val LOCATION_KEY = "current-location"
        private const val LOCATION_UPDATE_INTERVAL = 60000L
    }

    var currentLocation: Location?
        get() {
            return try {
                val location = keyValueStorage[LOCATION_KEY]
                location?.let {
                    Location.decodeJson(JSONObject(it), dateFormatting)
                }
            } catch (e: JSONException) {
                log.w("Failed to decode last known location JSON: $e")
                null
            }
        }
        set(value) {
            keyValueStorage[LOCATION_KEY] = value?.encodeJson(dateFormatting).toString()
        }

    private val locationChanges = subject
        .observeOn(ioScheduler)
        .map { locationResult ->
            val lastLocation = locationResult.lastLocation ?: return@map null
            // attempt to use Android's synchronous built-in geocoder api:
            val androidGeocoderAddress = try {
                geocoder.getFromLocation(
                    lastLocation.latitude,
                    lastLocation.longitude,
                    1
                )?.firstOrNull()
            } catch (exception: Exception) {
                log.w("Unable to use Android Geocoder API: $exception")
                null
            }

            val address = androidGeocoderAddress.whenNotNull { address ->
                Location.Address(
                    street = "${address.subThoroughfare} ${address.thoroughfare}",
                    city = address.locality,
                    state = address.adminArea,
                    country = address.countryName,
                    postalCode = address.postalCode,
                    isoCountryCode = address.countryCode,
                    subAdministrativeArea = address.subAdminArea,
                    subLocality = address.subLocality
                )
            }

            if (address == null) {
                log.w("Unable to geocode address for current coordinates.")
            }

            Location(
                address = address,
                coordinate = Location.Coordinate(
                    lastLocation.latitude,
                    lastLocation.longitude
                ),
                altitude = lastLocation.altitude,
                verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastLocation.hasVerticalAccuracy()) lastLocation.verticalAccuracyMeters.toDouble() else -1.0,
                horizontalAccuracy = if (lastLocation.hasAccuracy()) lastLocation.accuracy.toDouble() else -1.0,
                timestamp = Date()
            )
        }
        .filterNulls()
        .observeOn(mainScheduler)
        .share()

    override val locationUpdates = Publishers.concat(Publishers.just(currentLocation).filterNulls(), locationChanges).shareAndReplay(1)

    private val googleForegroundCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            newGoogleLocationResult(locationResult)
        }
    }

    init {
        if (privacyService.trackingMode == PrivacyService.TrackingMode.Default) {
            startMonitoring()
        }

        locationChanges
            .subscribe { location ->
                if (currentLocation == null || currentLocation?.isSignificantDisplacement(location) == true) {
                    if (trackLocation) {
                        currentLocation = location
                        locationReportingService.updateLocation(location)
                        log.d("updated location BackgroundLocationService")
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        log.i("Starting location monitoring.")
        permissionsNotifier.notifyForAnyOfPermission(
            setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        ).subscribe {
            val locationRequest = LocationRequest
                .create()
                .setInterval(LOCATION_UPDATE_INTERVAL)
                .setFastestInterval(LOCATION_UPDATE_INTERVAL)
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setSmallestDisplacement(MINIMUM_DISPLACEMENT_DISTANCE)

            log.v("Starting up foreground-only location tracking.")

            fusedLocationProviderClient
                .requestLocationUpdates(
                    locationRequest,
                    googleForegroundCallback,
                    Looper.getMainLooper()
                )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // The background version of Google's Fused Location Provider (that uses a
                // PendingIntent) appears to be incompatible with Android 12. FusedLocationProvider
                // expects a mutable pending intent, and Android 12 forbids you from creating one. 🤪
                log.v("Starting up background location tracking.")
                fusedLocationProviderClient
                    .requestLocationUpdates(
                        locationRequest,
                        PendingIntent.getBroadcast(
                            applicationContext,
                            0,
                            Intent(applicationContext, LocationBroadcastReceiver::class.java),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                FLAG_IMMUTABLE
                            } else {
                                0
                            }
                        )
                    ).addOnFailureListener { error ->
                        log.w("Unable to configure Rover location updates receiver because: $error")
                    }.addOnSuccessListener { _ ->
                        log.v("Now monitoring location updates.")
                    }
            }
        }
    }

    private fun stopMonitoring() {
        log.i("Stopping location monitoring.")
        fusedLocationProviderClient.removeLocationUpdates(googleForegroundCallback)
    }

    override fun onTrackingModeChanged(trackingMode: PrivacyService.TrackingMode) {
        if (trackingMode == PrivacyService.TrackingMode.Default) {
            startMonitoring()
        } else {
            stopMonitoring()
            currentLocation = null
        }
    }
}

class LocationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent)
            if (result == null) {
                log.w("Received a location result from Google, but result could not be extracted.")
                return
            }
            val rover = Rover.failableShared
            if (rover == null) {
                log.e("Received a location result from Google, but Rover is not initialized.  Ignoring.")
                return
            }
            val privacyService = rover.resolve(PrivacyService::class.java)
            if (privacyService == null) {
                log.e("Received a location result from Google, but the Rover PrivacyService is missing. Ensure that LocationAssembler is added to Rover.initialize(). Ignoring.")
                return
            }
            if (privacyService.trackingMode != PrivacyService.TrackingMode.Default) return
            val backgroundLocationService = rover.resolve(GoogleBackgroundLocationServiceInterface::class.java)
            if (backgroundLocationService == null) {
                log.e("Received a location result from Google, but the Rover GoogleBackgroundLocationServiceInterface is missing. Ensure that LocationAssembler is added to Rover.initialize(). Ignoring.")
                return
            } else {
                backgroundLocationService.newGoogleLocationResult(result)
            }
        } else {
            log.v("LocationReceiver received an intent, but it lacked a location result. Ignoring. Intent extras were ${intent.extras}")
        }
    }
}
