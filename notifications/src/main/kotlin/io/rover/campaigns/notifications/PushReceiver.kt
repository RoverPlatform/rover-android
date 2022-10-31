package io.rover.campaigns.notifications

import android.os.Bundle
import io.rover.campaigns.notifications.domain.Notification
import io.rover.campaigns.notifications.graphql.decodeJson
import io.rover.campaigns.core.events.PushTokenTransmissionChannel
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.DateFormattingInterface
import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException

open class PushReceiver(
    private val pushTokenTransmissionChannel: PushTokenTransmissionChannel,
    private val notificationDispatcher: NotificationDispatcher,
    private val dateFormatting: DateFormattingInterface,
    private val influenceTrackerService: InfluenceTrackerServiceInterface
) : PushReceiverInterface {

    override fun onTokenRefresh(token: String?) {
        pushTokenTransmissionChannel.setPushToken(token)
    }

    /**
     * Process an the parameters from an incoming notification.
     *
     * Note that this is running in the context of a 10 second wallclock execution time restriction.
     */
    override fun onMessageReceivedData(parameters: Map<String, String>) {
        // if we have been called, then:
        // a) the notification does not have a display message component; OR
        // b) the app is running in foreground.

        if (!parameters.containsKey("rover")) {
            log.w("A push notification received that appeared to be not intended for Rover Campaigns: `rover` data parameter not present. Ignoring.")
            // clear influenced open data so we don't take credit for an influenced open for a
            // notification we did not receive.
            influenceTrackerService.nonRoverPushReceived()
            return
        }

        log.v("Received a push notification. Raw parameters: $parameters")

        val notificationJson = parameters["rover"] ?: return
        handleRoverNotificationObject(notificationJson)
    }

    override fun onMessageReceivedDataAsBundle(parameters: Bundle) {
        val rover = parameters.getString("rover") ?: return
        handleRoverNotificationObject(rover)
    }

    private fun handleRoverNotificationObject(roverJson: String) {
        val notification = try {
            val notificationJsonObject = JSONObject(roverJson).getJSONObject("notification")
            Notification.decodeJson(
                notificationJsonObject,
                dateFormatting
            )
        } catch (e: JSONException) {
            log.w("Invalid push notification action received, because: '${e.message}'. Ignoring.")
            log.w("... contents were: $roverJson")
            return
        } catch (e: MalformedURLException) {
            log.w("Invalid push notification action received, because: '${e.message}'. Ignoring.")
            log.w("... contents were: $roverJson")
            return
        }

        notificationDispatcher.ingest(notification)
    }

    override fun onMessageReceivedNotification(notification: Notification) {
        notificationDispatcher.ingest(notification)
    }
}
