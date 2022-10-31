@file:JvmName("Location")

package io.rover.campaigns.location

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Geocoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.MessagesClient
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.container.Assembler
import io.rover.campaigns.core.container.Container
import io.rover.campaigns.core.container.Resolver
import io.rover.campaigns.core.container.Scope
import io.rover.campaigns.core.data.sync.CursorState
import io.rover.campaigns.core.data.sync.RealPagedSyncParticipant
import io.rover.campaigns.core.data.sync.SyncCoordinatorInterface
import io.rover.campaigns.core.data.sync.SyncParticipant
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.permissions.PermissionsNotifierInterface
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.location.events.contextproviders.LocationContextProvider
import io.rover.campaigns.location.sync.BeaconSyncDecoder
import io.rover.campaigns.location.sync.BeaconsRepository
import io.rover.campaigns.location.sync.BeaconsSqlStorage
import io.rover.campaigns.location.sync.BeaconsSyncResource
import io.rover.campaigns.location.sync.GeofenceSyncDecoder
import io.rover.campaigns.location.sync.GeofencesRepository
import io.rover.campaigns.location.sync.GeofencesSqlStorage
import io.rover.campaigns.location.sync.GeofencesSyncResource
import io.rover.campaigns.location.sync.LocationDatabase

/**
 * Location Assembler contains the Rover SDK subsystems for Geofence, Beacon, and location tracking.
 *
 * It can automatically use the Google Location services, which you can opt out of by passing false
 * to the following boolean parameters.  You may wish to do this if you want to use a Location SDK
 * from a vendor other than Google, integrate with your own location implementation, or do not
 * require the functionality.
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
     * Note that if you enable either [automaticGeofenceMonitoring] or [automaticBeaconMonitoring]
     * that the Google Fused Location Provider API will still be used, although location reporting
     * itself will be disabled as requested.
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
            SQLiteOpenHelper::class.java,
            "location"
        ) { resolver ->
            LocationDatabase(
                resolver.resolveSingletonOrFail(Context::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SQLiteDatabase::class.java,
            "location"
        ) { resolver ->
            resolver.resolveSingletonOrFail(SQLiteOpenHelper::class.java, "location").writableDatabase
        }

        container.register(
            Scope.Singleton,
            GeofencesSqlStorage::class.java
        ) { resolver ->
            GeofencesSqlStorage(
                resolver.resolveSingletonOrFail(
                    SQLiteDatabase::class.java,
                    "location"
                )
            )
        }

        container.register(
            Scope.Singleton,
            Geocoder::class.java
        ) { resolver ->
            Geocoder(resolver.resolveSingletonOrFail(Context::class.java))
        }

        container.register(
            Scope.Singleton,
            SyncParticipant::class.java,
            "geofences"
        ) { resolver ->
            RealPagedSyncParticipant(
                GeofencesSyncResource(
                    resolver.resolveSingletonOrFail(GeofencesSqlStorage::class.java)
                ),
                GeofenceSyncDecoder(),
                "io.rover.location.geofencesCursor",
                resolver.resolveSingletonOrFail(SQLiteOpenHelper::class.java, "location") as CursorState
            )
        }

        container.register(
            Scope.Singleton,
            GeofencesRepository::class.java
        ) { resolver ->
            GeofencesRepository(
                resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                resolver.resolveSingletonOrFail(GeofencesSqlStorage::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io")
            )
        }

        container.register(
            Scope.Singleton,
            BeaconsSqlStorage::class.java
        ) { resolver ->
            BeaconsSqlStorage(
                resolver.resolveSingletonOrFail(
                    SQLiteDatabase::class.java,
                    "location"
                )
            )
        }

        container.register(
            Scope.Singleton,
            SyncParticipant::class.java,
            "beacons"
        ) { resolver ->
            RealPagedSyncParticipant(
                BeaconsSyncResource(
                    resolver.resolveSingletonOrFail(BeaconsSqlStorage::class.java)
                ),
                BeaconSyncDecoder(),
                "io.rover.location.beaconsCursor",
                resolver.resolveSingletonOrFail(SQLiteOpenHelper::class.java, "location") as CursorState
            )
        }

        container.register(
            Scope.Singleton,
            BeaconsRepository::class.java
        ) { resolver ->
            BeaconsRepository(
                resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                resolver.resolveSingletonOrFail(BeaconsSqlStorage::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io")
            )
        }

        container.register(
            Scope.Singleton,
            ContextProvider::class.java,
            "location"
        ) { resolver ->
            LocationContextProvider(
                resolver.resolveSingletonOrFail(GoogleBackgroundLocationServiceInterface::class.java)
            )
        }

        // if automatic location/region tracking, then register our Google-powered services:
        if (automaticLocationTracking) {
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
                    resolver.resolveSingletonOrFail(LocationReportingServiceInterface::class.java),
                    resolver.resolveSingletonOrFail(Geocoder::class.java),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                    automaticLocationTracking,
                    resolver.resolveSingletonOrFail(LocalStorage::class.java),
                    resolver.resolveSingletonOrFail(DateFormattingInterface::class.java)
                )
            }
        }

        if (automaticGeofenceMonitoring) {
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
                    resolver.resolveSingletonOrFail(LocalStorage::class.java),
                    resolver.resolveSingletonOrFail(GeofencingClient::class.java),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                    resolver.resolveSingletonOrFail(
                        LocationReportingServiceInterface::class.java
                    ),
                    resolver.resolveSingletonOrFail(PermissionsNotifierInterface::class.java),
                    resolver.resolveSingletonOrFail(GeofencesRepository::class.java),
                    resolver.resolveSingletonOrFail(GoogleBackgroundLocationServiceInterface::class.java)
                )
            }

            container.register(
                Scope.Singleton,
                GeofenceServiceInterface::class.java
            ) { resolver -> resolver.resolveSingletonOrFail(GoogleGeofenceServiceInterface::class.java) }
        }

        if (automaticBeaconMonitoring) {
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
                    resolver.resolveSingletonOrFail(BeaconsRepository::class.java),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                    resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                    resolver.resolveSingletonOrFail(
                        LocationReportingServiceInterface::class.java
                    ),
                    resolver.resolveSingletonOrFail(PermissionsNotifierInterface::class.java)
                )
            }
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        if (automaticLocationTracking || automaticBeaconMonitoring || automaticGeofenceMonitoring) {
            // greedily poke for GoogleBackgroundLocationService to force the DI to evaluate
            // it and therefore have it start monitoring.
            resolver.resolveSingletonOrFail(GoogleBackgroundLocationServiceInterface::class.java)
        }

        if (automaticGeofenceMonitoring) {
            resolver.resolveSingletonOrFail(
                SyncCoordinatorInterface::class.java
            ).registerParticipant(
                resolver.resolveSingletonOrFail(
                    SyncParticipant::class.java,
                    "geofences"
                )
            )

            resolver.resolveSingletonOrFail(GoogleGeofenceServiceInterface::class.java)
        }

        if (automaticBeaconMonitoring) {
            resolver.resolveSingletonOrFail(
                SyncCoordinatorInterface::class.java
            ).registerParticipant(
                resolver.resolveSingletonOrFail(
                    SyncParticipant::class.java,
                    "beacons"
                )
            )

            resolver.resolveSingletonOrFail(GoogleBeaconTrackerServiceInterface::class.java)
        }

        if (automaticLocationTracking) {
            resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java).addContextProvider(
                resolver.resolveSingletonOrFail(ContextProvider::class.java, "location")
            )
        }
    }
}

@Deprecated("Use .resolve(GoogleBackgroundLocationServiceInterface::class.java)")
val RoverCampaigns.googleBackgroundLocationService: GoogleBackgroundLocationServiceInterface
    get() = this.resolve(GoogleBackgroundLocationServiceInterface::class.java) ?: throw missingDependencyError("GoogleBackgroundLocationServiceInterface")

@Deprecated("Use .resolve(GoogleBeaconTrackerServiceInterface::class.java)")
val RoverCampaigns.googleBeaconTrackerService: GoogleBeaconTrackerServiceInterface
    get() = this.resolve(GoogleBeaconTrackerServiceInterface::class.java) ?: throw missingDependencyError("GoogleBeaconTrackerServiceInterface")

@Deprecated("Use .resolve(GoogleGeofenceServiceInterface::class.java)")
val RoverCampaigns.googleGeofenceService: GoogleGeofenceServiceInterface
    get() = this.resolve(GoogleGeofenceServiceInterface::class.java) ?: throw missingDependencyError("GoogleGeofenceServiceInterface")

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include LocationAssembler() in the assembler list?")
}
