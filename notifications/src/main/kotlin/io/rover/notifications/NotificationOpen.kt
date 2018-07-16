package io.rover.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.rover.core.data.domain.AttributeValue
import io.rover.core.events.EventQueueService
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.routing.Router
import io.rover.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.notifications.domain.Notification
import io.rover.notifications.graphql.decodeJson
import org.json.JSONObject

/**
 * Open a notification by executing its [PushNotificationAction].
 */
open class NotificationOpen(
    private val applicationContext: Context,
    private val dateFormatting: DateFormattingInterface,
    private val eventsService: EventQueueServiceInterface,
    private val router: Router,
    private val openAppIntent: Intent,
    private val embeddedWebBrowserDisplay: EmbeddedWebBrowserDisplayInterface
): NotificationOpenInterface {
    override fun pendingIntentForAndroidNotification(notification: Notification): PendingIntent {
        return TransientNotificationLaunchActivity.generateLaunchIntent(
            applicationContext,
            notification
        )
    }

    private fun intentForNotification(notification: Notification): Intent {
        return when(notification.tapBehavior) {
            // TODO: will inject this an Intent with a name!
            is Notification.TapBehavior.OpenApp -> openAppIntent
            is Notification.TapBehavior.OpenUri -> router.route(
                notification.tapBehavior.uri,
                false
            )
            is Notification.TapBehavior.PresentWebsite -> embeddedWebBrowserDisplay.intentForViewingWebsiteViaEmbeddedBrowser(
                notification.tapBehavior.url.toString()
            )
        }
    }

    override fun intentForOpeningNotificationFromJson(notificationJson: String): Intent {
        // side-effect: issue open event.
        val notification = Notification.decodeJson(JSONObject(notificationJson), dateFormatting)

        issueNotificationOpenedEvent(
            notification.id,
            notification.campaignId,
            NotificationSource.Push
        )

        return intentForNotification(notification)
    }

    override fun intentForOpeningNotificationDirectly(notification: Notification): Intent? {
        // we only want to open the given notification's action in the case where it would
        // navigate somewhere useful, not just re-open the app.

        // Not appropriate to re-open the app when opening notification directly, do nothing.

        issueNotificationOpenedEvent(
            notification.id,
            notification.campaignId,
            NotificationSource.NotificationCenter
        )

        return intentForNotification(notification)
    }

    protected fun issueNotificationOpenedEvent(
        notificationId: String,
        campaignId: String,
        source: NotificationSource
    ) {
        eventsService.trackEvent(
            Event(
                "Notification Opened",
                hashMapOf(
                    Pair("notificationID", AttributeValue.String(notificationId)),
                    Pair("source", AttributeValue.String(source.wireValue)),
                    Pair("campaignID", AttributeValue.String(campaignId))
                )
            ),
            EventQueueService.ROVER_NAMESPACE
        )
    }

    override fun appOpenedAfterReceivingNotification(
        notificationId: String,
        campaignId: String
    ) {
        issueNotificationOpenedEvent(
            notificationId,
            campaignId,
            NotificationSource.InfluencedOpen
        )
    }

    enum class NotificationSource(val wireValue: String) {
        NotificationCenter("notificationCenter"),
        Push("pushNotification"),
        InfluencedOpen("influencedOpen")
    }
}
