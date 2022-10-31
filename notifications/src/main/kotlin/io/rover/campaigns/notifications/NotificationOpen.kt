package io.rover.campaigns.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.rover.campaigns.core.events.EventQueueService
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.domain.Event
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.routing.Router
import io.rover.campaigns.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.campaigns.notifications.domain.Notification
import io.rover.campaigns.notifications.domain.events.asAttributeValue
import io.rover.campaigns.notifications.graphql.decodeJson
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
            is Notification.TapBehavior.OpenUri -> router.route(notification.tapBehavior.uri, false)
            is Notification.TapBehavior.PresentWebsite -> embeddedWebBrowserDisplay.intentForViewingWebsiteViaEmbeddedBrowser(
                notification.tapBehavior.url.toString()
            )
        }
    }

    override fun intentForOpeningNotificationFromJson(notificationJson: String): Intent? {
        // side-effect: issue open event.
        val notification = Notification.decodeJson(JSONObject(notificationJson), dateFormatting)

        issueNotificationOpenedEvent(
            notification,
            NotificationSource.Push
        )

        return intentForNotification(notification)
    }

    override fun intentForOpeningNotificationDirectly(notification: Notification): Intent? {
        // we only want to open the given notification's action in the case where it would
        // navigate somewhere useful, not just re-open the app.

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
