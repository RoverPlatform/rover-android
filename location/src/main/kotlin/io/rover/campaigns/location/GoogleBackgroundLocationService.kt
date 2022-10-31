package io.rover.campaigns.location

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
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.data.domain.Location
import io.rover.campaigns.core.data.domain.Location.Companion.MINIMUM_DISPLACEMENT_DISTANCE
import io.rover.campaigns.core.data.graphql.operations.data.decodeJson
import io.rover.campaigns.core.data.graphql.operations.data.encodeJson
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.permissions.PermissionsNotifierInterface
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.platform.whenNotNull
import io.rover.campaigns.core.streams.PublishSubject
import io.rover.campaigns.core.streams.Publishers
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.core.streams.filterNulls
import io.rover.campaigns.core.streams.map
import io.rover.campaigns.core.streams.observeOn
import io.rover.campaigns.core.streams.share
import io.rover.campaigns.core.streams.shareAndReplay
import io.rover.campaigns.core.streams.subscribe
import org.json.JSONException
import org.json.JSONObject
import java.util.Date

/**
 * Subscribes to Location Updates from FusedLocationManager and emits location reporting events.
 *
 * This will allow you to see up to date location data for your users in the Rover Audience app if
 * [trackLocation] is enabled.
 *
 * Google documentation: https://developer.android.com/training/location/receive-location-updates.html
 */
class GoogleBackgroundLocationService(
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val applicationContext: Context,
    private val permissionsNotifier: PermissionsNotifierInterface,
    private val locationReportingService: LocationReportingServiceInterface,
    private val geocoder: Geocoder,
    ioScheduler: Scheduler,
    mainScheduler: Scheduler,
    private val trackLocation: Boolean = false,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface
) : GoogleBackgroundLocationServiceInterface {

    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    override fun newGoogleLocationResult(locationResult: LocationResult) {
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

    init {
        startMonitoring()

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
                    object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            newGoogleLocationResult(locationResult)
                        }
                    },
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
}

class LocationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent)
            if (result == null) {
                log.w("Received a location result from Google, but result could not be extracted.")
                return
            }
            val rover = RoverCampaigns.shared
            if (rover == null) {
                log.e("Received a location result from Google, but Rover Campaigns is not initialized.  Ignoring.")
                return
            }
            val backgroundLocationService = rover.resolve(GoogleBackgroundLocationServiceInterface::class.java)
            if (backgroundLocationService == null) {
                log.e("Received a location result from Google, but the Rover Campaigns GoogleBackgroundLocationServiceInterface is missing. Ensure that LocationAssembler is added to RoverCampaigns.initialize(). Ignoring.")
                return
            } else {
                backgroundLocationService.newGoogleLocationResult(result)
            }
        } else {
            log.v("LocationReceiver received an intent, but it lacked a location result. Ignoring. Intent extras were ${intent.extras}")
        }
    }
}
