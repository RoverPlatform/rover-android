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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.rover.sdk.core.Rover
import io.rover.sdk.core.events.EventQueueService
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.asAndroidUri
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.domain.events.asAttributeValue
import io.rover.sdk.notifications.graphql.decodeJson
import org.json.JSONObject

/**
 * Open a notification by executing its [Notification.TapBehavior].
 */
open class NotificationOpen(
    private val applicationContext: Context,
    private val dateFormatting: DateFormattingInterface,
    private val eventsService: EventQueueServiceInterface,
    private val router: Router,
    private val openAppIntent: Intent?,
    private val embeddedWebBrowserDisplay: EmbeddedWebBrowserDisplayInterface
) : NotificationOpenInterface {
    override fun pendingIntentForAndroidNotification(notification: Notification): PendingIntent {
        return TransientNotificationLaunchActivity.generateLaunchIntent(
            applicationContext,
            notification
        )
    }

    private fun intentForNotification(notification: Notification): Intent? {
        return when (notification.tapBehavior) {
            is Notification.TapBehavior.OpenApp -> openAppIntent
            is Notification.TapBehavior.OpenUri -> router.route(notification.tapBehavior.uri) ?: Intent(Intent.ACTION_VIEW, notification.tapBehavior.uri.asAndroidUri())
            is Notification.TapBehavior.PresentWebsite -> embeddedWebBrowserDisplay.intentForViewingWebsiteViaEmbeddedBrowser(
                notification.tapBehavior.url.toString()
            )
        }
    }

    override fun intentForOpeningNotificationFromJson(notificationJson: String): Intent? {
        // side-effect: issue open event.
        val notification = Notification.decodeJson(JSONObject(notificationJson), dateFormatting)

        notification.conversionTags?.let { conversionTags ->
            val conversionTrackerService = Rover.shared.resolve(ConversionsTrackerService::class.java)
            conversionTrackerService?.trackConversions(conversionTags)
        }

        issueNotificationOpenedEvent(
            notification,
            NotificationSource.Push
        )

        return intentForNotification(notification)
    }

    override fun intentForOpeningNotificationDirectly(notification: Notification): Intent? {
        // we only want to open the given notification's action in the case where it would
        // navigate somewhere useful, not just re-open the app.

        notification.conversionTags?.let { conversionTags ->
            val conversionTrackerService = Rover.shared.resolve(ConversionsTrackerService::class.java)
            conversionTrackerService?.trackConversions(conversionTags)
        }

        issueNotificationOpenedEvent(
            notification,
            NotificationSource.NotificationCenter
        )

        return when (notification.tapBehavior) {
            // Not appropriate to re-open the app when opening notification directly, do nothing.
            is Notification.TapBehavior.OpenApp -> null
            else -> intentForNotification(notification)
        }
    }

    protected open fun issueNotificationOpenedEvent(
        notification: Notification,
        source: NotificationSource
    ) {
        eventsService.trackEvent(
            Event(
                "Notification Opened",
                hashMapOf(
                    Pair("notification", notification.asAttributeValue()),
                    Pair("source", source.wireValue)
                )
            ),
            EventQueueService.ROVER_NAMESPACE
        )
    }

    override fun appOpenedAfterReceivingNotification(
        notification: Notification
    ) {
        issueNotificationOpenedEvent(
            notification,
            NotificationSource.InfluencedOpen
        )
    }

    enum class NotificationSource(val wireValue: String) {
        NotificationCenter("notificationCenter"),
        Push("pushNotification"),
        InfluencedOpen("influencedOpen")
    }
}
