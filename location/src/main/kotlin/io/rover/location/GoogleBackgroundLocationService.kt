package io.rover.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.streams.subscribe

/**
 * Subscribes to Location Updates from FusedLocationManager and emits location reporting events.
 *
 * This allows you to see up to date location data for your users in the Audience app.
 * If left out, the other location functionality (Beacons and Geofences) will continue to work.
 *
 * Google documentation: https://developer.android.com/training/location/receive-location-updates.html
 */
class GoogleBackgroundLocationService(
    private val fusedLocationProviderClient: FusedLocationProviderClient,
    private val applicationContext: Context,
    private val permissionsNotifier: PermissionsNotifierInterface,
    private val locationReportingService: LocationReportingServiceInterface
): GoogleBackgroundLocationServiceInterface {
    override fun newGoogleLocationResult(locationResult: LocationResult) {
        log.v("Received location result: $locationResult")

        val location = LocationReportingServiceInterface.Location(
            locationResult.lastLocation.latitude,
            locationResult.lastLocation.longitude,
            locationResult.lastLocation.altitude,
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && locationResult.lastLocation.hasVerticalAccuracy()) locationResult.lastLocation.verticalAccuracyMeters else null,
            if(locationResult.lastLocation.hasAccuracy()) locationResult.lastLocation.accuracy else null
        )

        locationReportingService.updateLocation(location)
    }

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
                        .setSmallestDisplacement(0f)
                        .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY),
                    PendingIntent.getBroadcast(
                        applicationContext,
                        0,
                        Intent(applicationContext, LocationBroadcastReceiver::class.java),
                        0
                    )
                ).addOnFailureListener { error ->
                    log.w("Unable to configure Rover location updates receiver because: $error")
                }.addOnSuccessListener {
                    log.v("Now monitoring location updates.")
                }
        }
    }
}

class LocationBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val result = LocationResult.extractResult(intent)
            Rover.sharedInstance.resolveSingletonOrFail(GoogleBackgroundLocationServiceInterface::class.java).newGoogleLocationResult(result)
        } else {
            log.v("LocationReceiver received an intent, but it lacked a location result. Ignoring. Intent extras were ${intent.extras}")
        }
    }
}
