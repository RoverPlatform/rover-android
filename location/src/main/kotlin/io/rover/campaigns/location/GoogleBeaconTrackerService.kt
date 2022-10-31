package io.rover.campaigns.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.EddystoneUid
import com.google.android.gms.nearby.messages.IBeaconId
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageFilter
import com.google.android.gms.nearby.messages.MessageListener
import com.google.android.gms.nearby.messages.MessagesClient
import com.google.android.gms.nearby.messages.MessagesOptions
import com.google.android.gms.nearby.messages.NearbyPermissions
import com.google.android.gms.nearby.messages.Strategy
import com.google.android.gms.nearby.messages.SubscribeOptions
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.permissions.PermissionsNotifierInterface
import io.rover.campaigns.core.platform.whenNotNull
import io.rover.campaigns.core.streams.Publishers
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.core.streams.doOnNext
import io.rover.campaigns.core.streams.filter
import io.rover.campaigns.core.streams.map
import io.rover.campaigns.core.streams.observeOn
import io.rover.campaigns.core.streams.subscribe
import io.rover.campaigns.location.domain.Beacon
import io.rover.campaigns.location.sync.BeaconsRepository
import java.util.UUID

/**
 * Subscribes to Beacon updates from Nearby Messages API and emits events and emits beacon
 * reporting events.
 *
 * Google documentation: https://developers.google.com/nearby/messages/android/get-beacon-messages
 */
class GoogleBeaconTrackerService(
    private val applicationContext: Context,
    private val nearbyMessagesClient: MessagesClient,
    private val beaconsRepository: BeaconsRepository,
    mainScheduler: Scheduler,
    ioScheduler: Scheduler,
    private val locationReportingService: LocationReportingServiceInterface,
    permissionsNotifier: PermissionsNotifierInterface
) : GoogleBeaconTrackerServiceInterface {
    override fun newGoogleBeaconMessage(intent: Intent) {
        nearbyMessagesClient.handleIntent(
            intent,
            object : MessageListener() {
                override fun onFound(message: Message) {
                    log.v("A beacon found: $message")

                    emitEventForPossibleBeacon(message, true)
                }

                override fun onLost(message: Message) {
                    log.v("A beacon lost: $message")
                    emitEventForPossibleBeacon(message, false)
                }
            }
        )
    }

    /**
     * If the message matches a given [Beacon] in the database, and emits events as needed.
     */
    private fun emitEventForPossibleBeacon(message: Message, enter: Boolean) {
        return when (message.type) {
            Message.MESSAGE_TYPE_I_BEACON_ID -> {
                val ibeacon = IBeaconId.from(message)
                beaconsRepository.beaconByUuidMajorAndMinor(
                    ibeacon.proximityUuid,
                    ibeacon.major,
                    ibeacon.minor
                ).subscribe { beacon ->
                    beacon.whenNotNull {
                        if (enter) {
                            locationReportingService.trackEnterBeacon(it)
                        } else {
                            locationReportingService.trackExitBeacon(it)
                        }
                    }
                }
            }
            Message.MESSAGE_TYPE_EDDYSTONE_UID -> {
                val eddystoneUid = EddystoneUid.from(message)
                log.w("Eddystone beacons not currently supported by Rover SDK 2.0 (uid was $eddystoneUid), and it appears you have one registered with your project. Ignoring.")
            }
            else -> {
                log.w("Unknown beacon type: '${message.type}'. Full payload was '${message.content}'. Ignoring.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoringBeacons(uuids: Set<UUID>) {
        val messagesClient = Nearby.getMessagesClient(
            applicationContext,
            MessagesOptions.Builder()
                .setPermissions(NearbyPermissions.BLE)
                .build()
        )

        val messageFilters = uuids.map { uuid ->
            // for now we only support iBeacon filters. No Eddystone for now.
            MessageFilter.Builder()
                .includeIBeaconIds(uuid, null, null)
                .build()
        }

        val subscribeOptions = SubscribeOptions.Builder()
            .setStrategy(Strategy.BLE_ONLY)
            .apply { messageFilters.forEach { this.setFilter(it) } }
            .build()

        messagesClient.subscribe(
            PendingIntent.getBroadcast(
                applicationContext,
                0,
                Intent(applicationContext, BeaconBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { FLAG_IMMUTABLE } else { 0 }
            ),
            subscribeOptions
        ).addOnFailureListener {
            log.w("Unable to configure Rover beacon tracking because: $it")
        }.addOnCompleteListener {
            log.w("Successfully registered for beacon tracking updates.")
        }
    }

    companion object {
        private const val BACKGROUND_LOCATION_PERMISSION_CODE = "android.permission.ACCESS_BACKGROUND_LOCATION"
        private const val Q_VERSION_CODE = 29
    }

    init {
        val fineLocationSource = Publishers.concat(
            Publishers.just(false),
            permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .map { it == Manifest.permission.ACCESS_FINE_LOCATION }
        )

        val backgroundLocationSource = Publishers.concat(
            Publishers.just(false),
            permissionsNotifier.notifyForPermission(BACKGROUND_LOCATION_PERMISSION_CODE)
                .map { it == BACKGROUND_LOCATION_PERMISSION_CODE }
        )

        // This publisher emits whenever all necessary permissions for starting beacon monitoring are granted.
        val permissionGranted = Publishers.combineLatest(fineLocationSource, backgroundLocationSource) {
                fineLocationGranted, _ ->
            val backgroundPermissionGranted = (ContextCompat.checkSelfPermission(applicationContext, BACKGROUND_LOCATION_PERMISSION_CODE) == PackageManager.PERMISSION_GRANTED)

            // The BACKGROUND_LOCATION_PERMISSION_CODE permission is required for monitoring for
            // beacons on Q and above.
            fineLocationGranted && (Build.VERSION.SDK_INT < Q_VERSION_CODE || backgroundPermissionGranted)
        }.filter { it }

        Publishers.combineLatest(
            // observeOn(mainScheduler) used on each because combineLatest() is not thread safe.
            permissionGranted.doOnNext { log.v("Permission obtained. $it") },
            beaconsRepository.allBeacons().observeOn(mainScheduler).doOnNext { log.v("Full beacons list obtained from sync.") }
        ) { permission, beacons ->
            Pair(permission, beacons)
        }.observeOn(ioScheduler).map { (_, beacons) ->
            val fetchedBeacons = beacons.iterator().use { it.asSequence().toList() }
            fetchedBeacons.aggregateToUniqueUuids().apply {
                log.v("Starting up beacon tracking for ${fetchedBeacons.count()} beacon(s), aggregated to ${count()} filter(s).")
            }
        }.observeOn(mainScheduler).subscribe { beaconUuids ->
            startMonitoringBeacons(beaconUuids)
        }
    }
}

class BeaconBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val rover = RoverCampaigns.shared
        if (rover == null) {
            log.e("Received a beacon result from Google, but Rover Campaigns is not initialized.  Ignoring.")
            return
        }
        val beaconTrackerService = rover.resolve(GoogleBeaconTrackerServiceInterface::class.java)
        if (beaconTrackerService == null) {
            log.e("Received a beacon result from Google, but GoogleBeaconTrackerServiceInterface is not registered in the Rover Campaigns container. Ensure LocationAssembler() is in RoverCampaigns.initialize(). Ignoring.")
            return
        } else beaconTrackerService.newGoogleBeaconMessage(
            intent
        )
    }
}

/**
 * We can tell Google Nearby to filter only on UUID, effectively wildcarding the major and minor.b
 *
 * This allows us to use far fewer filters, avoiding hitting undocumented but existent limits.
 */
fun Collection<Beacon>.aggregateToUniqueUuids(): Set<UUID> {
    return this.map { it.uuid }.toSet()
}
