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

package io.rover.sdk.notifications.communicationhub

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import io.rover.sdk.core.logging.log

/**
 * Cancels delivered Hub notifications (posts and conversations) as part of a 410 reset.
 *
 * Extracted behind an interface so `HubSyncCoordinator` can be exercised without a real
 * `NotificationManager`.
 */
internal interface DeliveredHubNotificationClearer {
    /**
     * Cancels every delivered notification carrying a Hub push marker.
     *
     * Selection is by [HubPushNotification.kindOf] marker, not by Room contents: any notification
     * already delivered at reset time predates the identity change that triggered the 410, so it is
     * stale regardless of whether its row still exists — and if left in the tray it points at
     * previous-identity content that has just been dropped. Classic/deep-link notifications carry no
     * marker and are left in place.
     */
    fun clearDeliveredHubNotifications()
}

internal class AndroidDeliveredHubNotificationClearer(
    private val applicationContext: Context,
) : DeliveredHubNotificationClearer {
    override fun clearDeliveredHubNotifications() {
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val hubNotifications = notificationManager.activeNotifications.filter { statusBarNotification ->
            HubPushNotification.kindOf(statusBarNotification.notification) != null
        }
        if (hubNotifications.isEmpty()) return

        log.i("Clearing ${hubNotifications.size} delivered Hub notification(s) on 410 reset.")
        hubNotifications.forEach { statusBarNotification ->
            notificationManager.cancel(statusBarNotification.tag, statusBarNotification.id)
        }
    }
}
