package io.rover.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.platform.whenNotNull
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.doOnNext
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.subscribe
import io.rover.location.domain.Beacon
import io.rover.location.sync.BeaconsRepository
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
        nearbyMessagesClient.handleIntent(intent, object : MessageListener() {
            override fun onFound(message: Message) {
                log.v("A beacon found: $message")

                emitEventForPossibleBeacon(message, true)
            }

            override fun onLost(message: Message) {
                log.v("A beacon lost: $message")
                emitEventForPossibleBeacon(message, false)
            }
        })
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
                        if(enter) {
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
        val messagesClient = Nearby.getMessagesClient(applicationContext, MessagesOptions.Builder()
            .setPermissions(NearbyPermissions.BLE)
            .build())

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
                PendingIntent.FLAG_UPDATE_CURRENT
            ),
            subscribeOptions
        ).addOnFailureListener {
            log.w("Unable to configure Rover beacon tracking because: $it")
        }.addOnCompleteListener {
            log.w("Successfully registered for beacon tracking updates.")
        }
    }

    init {
        Publishers.combineLatest(
            // observeOn(mainScheduler) used on each because combineLatest() is not thread safe.
            permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).observeOn(mainScheduler).doOnNext { log.v("Permission obtained.") },
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
        val rover = Rover.shared
        if(rover == null) {
            log.e("Received a beacon result from Google, but Rover is not initialized.  Ignoring.")
            return
        }
        val beaconTrackerService = rover.resolve(GoogleBeaconTrackerServiceInterface::class.java)
        if(beaconTrackerService == null) {
            log.e("Received a beacon result from Google, but GoogleBeaconTrackerServiceInterface is not registered in the Rover container. Ensure LocationAssembler() is in Rover.initialize(). Ignoring.")
            return
        }
        else beaconTrackerService.newGoogleBeaconMessage(
            intent
        )
    }
}

/**
 * We can tell Google Nearby to filter only on UUID, effectively wildcarding the major and minor.
 *
 * This allows us to use far fewer filters, avoiding hitting undocumented but existent limits.
 */
fun Collection<Beacon>.aggregateToUniqueUuids(): Set<UUID> {
    return this.map { it.uuid }.toSet()
}