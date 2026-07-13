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

package io.rover.sdk.notifications

import android.os.Bundle
import io.rover.sdk.core.events.PushTokenTransmissionChannel
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.notifications.communicationhub.HubPushKind
import io.rover.sdk.notifications.communicationhub.HubPushNotification
import io.rover.sdk.notifications.communicationhub.conversations.ConversationPushHandler
import io.rover.sdk.notifications.communicationhub.posts.PostPushHandler
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.graphql.decodeJson
import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException

internal open class PushReceiver(
    private val pushTokenTransmissionChannel: PushTokenTransmissionChannel,
    private val notificationDispatcher: NotificationDispatcher,
    private val dateFormatting: DateFormattingInterface,
    private val influenceTrackerService: InfluenceTrackerServiceInterface,
    internal val postPushHandler: PostPushHandler? = null,
    internal val conversationPushHandler: ConversationPushHandler? = null,
) : PushReceiverInterface {

    override fun onTokenRefresh(token: String?) {
        if (token == null) {
            log.w("Null push token received; ignoring to avoid clearing a previously valid token.")
            return
        }
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
            log.i("A push notification received that appeared to be not intended for Rover : `rover` data parameter not present. Ignoring.")
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
        val jsonObject = try {
            JSONObject(roverJson)
        } catch (e: JSONException) {
            log.w("Invalid push notification action received, because: '${e.message}'. Ignoring. (payload size: ${roverJson.length} bytes)")
            return
        }

        // Classify once using the shared predicate. The same classification decides what to insert
        // here and, via the marker stamped on the notification we post, what a 410 reset later
        // clears from the tray — keeping insert and clear from drifting apart.
        val hubPushKind = HubPushNotification.kind(jsonObject)
        when (hubPushKind) {
            HubPushKind.POST -> postPushHandler?.handleCommunicationHubPush(roverJson)
            HubPushKind.CONVERSATION ->
                // The predicate keys on the `conversation` object alone; only actually process a
                // conversation whose reply/participant siblings are also present.
                if (isConversationPushPayload(jsonObject)) {
                    conversationPushHandler?.handleCommunicationHubPush(roverJson)
                }
            null -> { /* not a Hub push */ }
        }

        val notification = try {
            if (!jsonObject.has("notification")) {
                return
            }

            val notificationJsonObject = jsonObject.getJSONObject("notification")

            Notification.decodeJson(
                notificationJsonObject,
                dateFormatting
            )
        } catch (e: MalformedURLException) {
            log.w("Invalid push notification action received, because: '${e.message}'. Ignoring.")
            log.w("... contents were: $roverJson")
            return
        }

        // Pass the Hub kind through so the dispatched tray notification carries the marker a 410
        // reset uses to clear it. For a post push this marks the post's notification; classic
        // campaign pushes classify as null and stay unmarked (and untouched by reset).
        notificationDispatcher.ingest(notification, hubPushKind)
    }

    private fun isConversationPushPayload(jsonObject: JSONObject): Boolean {
        return jsonObject.has("conversation") && jsonObject.has("reply") && jsonObject.has("participant")
    }

    override fun onMessageReceivedNotification(notification: Notification) {
        notificationDispatcher.ingest(notification)
    }
}
