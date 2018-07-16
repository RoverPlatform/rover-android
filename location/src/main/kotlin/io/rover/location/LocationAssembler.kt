package io.rover.location

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.MessagesClient
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.data.state.StateManagerServiceInterface
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.streams.Scheduler

/**
 * Location Assembler contains the Rover SDK subsystems for Geofence, Beacon, and location tracking.
 *
 * It can automatically use the Google Location services, which you can opt out of by passing false
 * to the following boolean parameters (which you may wish to do if you want to use a Location SDK
 * from a vendor other than Google, integrate with your own location implementation, or do not
 * require the functionality, then you may wish to consider passing false below.
 *
 * Note: if you use any of the below, then you must complete the Google Play Services setup as per
 * the SDK documentation (also needed for the Notifications module).
 */
class LocationAssembler(
    /**
     * Automatically use the Google Location Geofence API to monitor for geofence events.
     *
     * Note: because of a fixed limit of geofences that may be monitored with the Google Geofence
     * API, this may introduce conflicts with your own code.  In that case, see below.
     *
     * Set to false if you do not want Rover to provide this functionality, or if you prefer to
     * manage the Geofences yourself (see [GoogleGeofenceService]).
     */
    private val automaticGeofenceMonitoring: Boolean = true,

    /**
     * Automatically use the Google Nearby Messages API to monitor for Beacons.
     *
     * This should not conflict with your own use of Nearby Messages API, so you can leave
     * [automaticBeaconMonitoring] true even if you are using Google Nearby for your own beacons or
     * messages.
     *
     * Set to false if you do not want Rover to provide this functionality, or if you still prefer
     * to manage the Beacons yourself (see [GoogleBeaconTrackerService]).
     */
    private val automaticBeaconMonitoring: Boolean = true,

    /**
     * Automatically use the Google Location [FusedLocationProviderClient] to track the device and
     * user's location in the background.  This will make more accurate location information
     * available about your users in the Rover Audience app.
     *
     * This should not conflict with your own use of the Google Fused Location Provider API.
     *
     * Set to false if you do not want Rover to provide this functionality, or if you still prefer
     * to manage the fused location manager yourself.
     */
    private val automaticLocationTracking: Boolean = true
) : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            LocationReportingServiceInterface::class.java
        ) { resolver ->
            LocationReportingService(
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            RegionRepositoryInterface::class.java
        ) { resolver ->
            RegionRepository(
                resolver.resolveSingletonOrFail(StateManagerServiceInterface::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main")
            )
        }

        // if automatic location/region tracking, then register our Google-powered services:
        if(automaticLocationTracking) {
            container.register(
                Scope.Singleton,
                FusedLocationProviderClient::class.java
            ) { resolver ->
                LocationServices.getFusedLocationProviderClient(
                    resolver.resolveSingletonOrFail(Context::class.java)
                )
            }

            container.register(
                Scope.Singleton,
                GoogleBackgroundLocationServiceInterface::class.java
            ) { resolver ->
                GoogleBackgroundLocationService(
                    resolver.resolveSingletonOrFail(
                        FusedLocationProviderClient::class.java
                    ),
                    resolver.resolveSingletonOrFail(Context::class.java),
                    resolver.resolveSingletonOrFail(PermissionsNotifierInterface::class.java),
                    resolver.resolveSingletonOrFail(LocationReportingServiceInterface::class.java)
                )
            }
        }

        if(automaticGeofenceMonitoring) {
            container.register(
                Scope.Singleton,
                GeofencingClient::class.java
            ) { resolver ->
                LocationServices.getGeofencingClient(
                    resolver.resolveSingletonOrFail(Context::class.java)
                )
            }

            container.register(
                Scope.Singleton,
                GoogleGeofenceServiceInterface::class.java
            ) { resolver ->
                GoogleGeofenceService(
                    resolver.resolveSingletonOrFail(Context::class.java),
                    resolver.resolveSingletonOrFail(GeofencingClient::class.java),
                    resolver.resolveSingletonOrFail(
                        LocationReportingServiceInterface::class.java
                    ),
                    resolver.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)
                )
            }
        }

        if(automaticBeaconMonitoring) {
            container.register(
                Scope.Singleton,
                MessagesClient::class.java
            ) { resolver ->
                Nearby.getMessagesClient(resolver.resolveSingletonOrFail(Context::class.java))
            }

            container.register(
                Scope.Singleton,
                GoogleBeaconTrackerServiceInterface::class.java
            ) { resolver ->
                GoogleBeaconTrackerService(
                    resolver.resolveSingletonOrFail(Context::class.java),
                    resolver.resolveSingletonOrFail(MessagesClient::class.java),
                    resolver.resolveSingletonOrFail(
                        LocationReportingServiceInterface::class.java
                    ),
                    resolver.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)
                )
            }
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        if(automaticGeofenceMonitoring) {
            // register our GoogleGeofenceService as an observer of the Rover regions (in this case,
            // for geofences), if the developer wants the automatic Google-powered solution.
            resolver.resolveSingletonOrFail(RegionRepositoryInterface::class.java).registerObserver(
                resolver.resolveSingletonOrFail(GoogleGeofenceServiceInterface::class.java)
            )
        }

        if(automaticLocationTracking) {
            // greedily poke for GoogleBackgroundLocationService to force the DI to evaluate
            // it and therefore have it start monitoring.
            resolver.resolveSingletonOrFail(GoogleBackgroundLocationServiceInterface::class.java)
        }

        if(automaticBeaconMonitoring) {
            // register our GoogleBeaconTrackerService as an observer of the Rover regions (in this
            // case, for beacons), if the developer wants the automatic Google-powered solution.
            resolver.resolveSingletonOrFail(RegionRepositoryInterface::class.java).registerObserver(
                resolver.resolveSingletonOrFail(GoogleBeaconTrackerServiceInterface::class.java)
            )
        }
    }
}
