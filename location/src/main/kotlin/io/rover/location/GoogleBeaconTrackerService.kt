package io.rover.location

import android.Manifest
import android.annotation.SuppressLint
import com.google.android.gms.nearby.messages.MessagesClient
import com.google.android.gms.nearby.messages.NearbyPermissions
import com.google.android.gms.nearby.messages.MessagesOptions
import com.google.android.gms.nearby.Nearby
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.nearby.messages.EddystoneUid
import com.google.android.gms.nearby.messages.IBeaconId
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageFilter
import com.google.android.gms.nearby.messages.MessageListener
import com.google.android.gms.nearby.messages.Strategy
import com.google.android.gms.nearby.messages.SubscribeOptions
import io.rover.location.domain.Region
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.streams.subscribe
import io.rover.core.platform.whenNotNull
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
    private val locationReportingService: LocationReportingServiceInterface,
    permissionsNotifier: PermissionsNotifierInterface
): GoogleBeaconTrackerServiceInterface {
    override fun newGoogleBeaconMessage(intent: Intent) {
        nearbyMessagesClient.handleIntent(intent, object : MessageListener() {
            override fun onFound(message: Message) {
                log.v("A beacon found: $message")
                messageToBeacon(message).whenNotNull {
                    locationReportingService.trackEnterBeacon(it)
                }
            }

            override fun onLost(message: Message) {
                log.v("A beacon lost: $message")
                messageToBeacon(message).whenNotNull {

                    locationReportingService.trackExitBeacon(it)
                }
            }
        })
    }

    override fun regionsUpdated(regions: List<Region>) {
        currentBeacons = regions.filterIsInstance(Region.BeaconRegion::class.java)

        startMonitoringBeaconsIfPossible()
    }

    private fun messageToBeacon(message: Message): Region.BeaconRegion? {
        return when(message.type) {
            Message.MESSAGE_TYPE_I_BEACON_ID -> {
                IBeaconId.from(message).toRoverBeaconRegion()
            }
            Message.MESSAGE_TYPE_EDDYSTONE_UID -> {
                val eddystoneUid = EddystoneUid.from(message)
                log.w("Eddystone beacons not currently supported by Rover (uid was $eddystoneUid), and it appears you have one registered with your project. Ignoring.")
                null
            }
            else -> {
                log.w("Unknown beacon type: '${message.type}'. Full payload was '${message.content}'. Ignoring.")
                null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoringBeaconsIfPossible() {
        if(permissionObtained && currentBeacons.isNotEmpty()) {
            log.v("Starting up beacon tracking.")
            val messagesClient = Nearby.getMessagesClient(applicationContext, MessagesOptions.Builder()
                .setPermissions(NearbyPermissions.BLE)
                .build())

            val messageFilters = currentBeacons.map { beaconRegion ->
                // for now we only support iBeacon filters. No Eddystone for now.
                MessageFilter.Builder()
                    .includeIBeaconIds(beaconRegion.uuid, beaconRegion.major, beaconRegion.minor)
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
    }

    /**
     * State: the current subset of beacons we're monitoring for.
     */
    private var currentBeacons: List<Region.BeaconRegion> = // listOf()
        listOf(
            Region.BeaconRegion(
            UUID.fromString("6A2C6579-1ED8-4307-A6FC-BA9A964EA508"), null, null
            ),
            Region.BeaconRegion(
                UUID.fromString("DB35B999-4488-4975-BFEF-7ED35961EF34"), null, null
            )
        )

    /**
     * State: have we been granted permission to use location services yet?
     */
    private var permissionObtained = false

    init {
        permissionsNotifier.notifyForPermission(Manifest.permission.ACCESS_FINE_LOCATION).subscribe {
            permissionObtained = true
            startMonitoringBeaconsIfPossible()
        }
    }
}

class BeaconBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Rover.sharedInstance.resolveSingletonOrFail(GoogleBeaconTrackerServiceInterface::class.java).newGoogleBeaconMessage(
            intent
        )
    }
}

/**
 * Map a received iBeacon back to a Rover.BeaconRegion value object.
 */
fun IBeaconId.toRoverBeaconRegion(): Region.BeaconRegion {
    return Region.BeaconRegion(
        this.proximityUuid,
        this.major,
        this.minor
    )
}
