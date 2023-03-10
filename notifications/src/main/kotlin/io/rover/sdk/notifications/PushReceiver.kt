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
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.graphql.decodeJson
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
            log.w("A push notification received that appeared to be not intended for Rover : `rover` data parameter not present. Ignoring.")
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
