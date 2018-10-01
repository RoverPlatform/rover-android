package io.rover.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.platform.whenNotNull
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Scheduler
import io.rover.core.streams.doOnNext
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribe
import io.rover.core.data.domain.Location
import org.reactivestreams.Publisher
import java.io.IOException
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
    /**
     * The minimum displacement in meters that will trigger a location update (and geofence/beacon
     * rebinding).
     */
    private val minimumDisplacement: Float = 500f
) : GoogleBackgroundLocationServiceInterface {
    override fun newGoogleLocationResult(locationResult: LocationResult) {
        log.v("Received location result: $locationResult")
        subject.onNext(locationResult)
    }

    private val subject = PublishSubject<LocationResult>()

    override val locationUpdates: Publisher<Location> = subject
        .observeOn(ioScheduler)
        .map { locationResult ->
            // attempt to use Android's synchronous built-in geocoder api:
            val androidGeocoderAddress = try {
                geocoder.getFromLocation(
                    locationResult.lastLocation.latitude,
                    locationResult.lastLocation.longitude,
                    1
                ).firstOrNull()
            } catch (ioException: IOException) {
                log.w("Unable to use Android Geocoder API: $ioException")
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

            if(address == null) {
                log.w("Unable to geocode address for current coordinates.")
            }

            Location(
                address = address,
                coordinate = Location.Coordinate(
                    locationResult.lastLocation.latitude,
                    locationResult.lastLocation.longitude
                ),
                altitude = locationResult.lastLocation.altitude,
                verticalAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && locationResult.lastLocation.hasVerticalAccuracy()) locationResult.lastLocation.verticalAccuracyMeters.toDouble() else -1.0,
                horizontalAccuracy = if (locationResult.lastLocation.hasAccuracy()) locationResult.lastLocation.accuracy.toDouble() else -1.0,
                timestamp = Date()
            )
        }
        .observeOn(mainScheduler)
        .doOnNext { location ->
            if(trackLocation) {
                locationReportingService.updateLocation(location)
            }
        }
        .shareHotAndReplay(1)

    init {
        startMonitoring()
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoring() {
        permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).subscribe {
            log.v("Starting up location tracking.")
            fusedLocationProviderClient
                .requestLocationUpdates(
                    LocationRequest
                        .create()
                        .setInterval(1)
                        .setFastestInterval(1)
                        .setSmallestDisplacement(minimumDisplacement)
                        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
                    PendingIntent.getBroadcast(
                        applicationContext,
                        0,
                        Intent(applicationContext, LocationBroadcastReceiver::class.java),
                        0
                    )
                ).addOnFailureListener { error ->
                    log.w("Unable to configure Rover location updates receiver because: $error")
                }.addOnSuccessListener { _ ->
                    log.v("Now monitoring location updates.")
                }
        }
    }
}

class LocationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent)
            Rover.sharedInstance.googleBackgroundLocationService.newGoogleLocationResult(result)
        } else {
            log.v("LocationReceiver received an intent, but it lacked a location result. Ignoring. Intent extras were ${intent.extras}")
        }
    }
}
