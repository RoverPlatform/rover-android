package io.rover.campaigns.notifications

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.notifications.domain.Notification
import io.rover.campaigns.notifications.graphql.decodeJson
import io.rover.campaigns.notifications.graphql.encodeJson
import io.rover.campaigns.notifications.ui.concerns.NotificationsRepositoryInterface
import org.json.JSONException
import org.json.JSONObject

/**
 * When the user taps a Rover notification created for the app by
 * [ActionIntentBackstackSynthesizer] in the Android notification tray, we want an
 * analytics event to be emitted as a side-effect.  However, the target screen could be either an
 * external application (particularly, a web browser) or some other Activity in the app that would
 * be difficult to instrument.
 *
 * So, this Activity will be responsible for emitting that that side-effect happens, although
 * it does so by delegating to [NotificationOpen].
 */
class TransientNotificationLaunchActivity : AppCompatActivity() {

    // TODO: make transparent/invisible somehow to avoid flicker

    override fun onStart() {
        super.onStart()

        val rover = RoverCampaigns.shared
        if (rover == null) {
            log.e("TransientNotificationLaunchActivity cannot be used before Rover Campaigns is initialized.")
            return
        }
        val notificationOpen = rover.resolve(NotificationOpenInterface::class.java)
        if (notificationOpen == null) {
            log.e("Could not resolve NotificationOpenInterface in Rover Campaigns container.  Ensure NotificationAssembler() is added to RoverCampaigns.initialize.")
            return
        }
        val influenceTracker = rover.resolve(InfluenceTrackerServiceInterface::class.java)
        if (influenceTracker == null) {
            log.e("Could not resolve InfluenceTrackerServiceInterface in Rover Campaigns container.  Ensure NotificationAssembler() is added to RoverCampaigns.initialize.")
            return
        }
        val dateFormatting = rover.resolve(DateFormattingInterface::class.java)
        if (dateFormatting == null) {
            log.e("Could not resolve DateFormattingInterface in Rover Campaigns container.  Ensure NotificationAssembler() is added to RoverCampaigns.initialize.")
            return
        }
        val notificationsRepository = rover.resolve(NotificationsRepositoryInterface::class.java)
        if (notificationsRepository == null) {
            log.e("Could not resolve NotificationsRepositoryInterface in the Rover Campaigns container.  Ensure NotificationAssembler() is added to RoverCampaigns.initialize.")
        }

        log.v("Transient notification launch activity running.")

        if (intent.getStringExtra(NOTIFICATION_JSON) == null) {
            log.e("No notification json passed, finishing activity")
            finish()
            return
        }

        // grab the notification back out of the arguments.
        val notificationJson = intent.getStringExtra(NOTIFICATION_JSON)

        // this will also do the side-effect of issuing the Notification Opened event, which
        // is the whole reason for this activity existing.

        try {
            val notification = Notification.decodeJson(
                JSONObject(notificationJson),
                dateFormatting
            )
            notificationsRepository?.markRead(notification)
        } catch (e: JSONException) {
            log.w("Badly formed notification, could not mark it as read.")
        }

        influenceTracker.notificationOpenedDirectly()

        val intent = notificationJson?.let { notificationOpen.intentForOpeningNotificationFromJson(it) } ?: return

        try {
            ContextCompat.startActivity(
                this,
                intent,
                null
            )
        } catch (e: ActivityNotFoundException) {
            log.e(
                "No activity could be found to handle the Intent needed to start the notification.\n" +
                    "This could be because the deep link scheme slug you set on NotificationsAssembler does not match what is set in your Rover account, or that an Activity is missing from your manifest.\n\n" +
                    "Intent was: $intent"
            )
            finish()
            return
        }

        finish()
    }

    companion object {
        fun generateLaunchIntent(
            context: Context,
            notification: Notification
        ): PendingIntent {
            val notificationJson = notification.encodeJson(
                (
                    RoverCampaigns.shared
                        ?: throw RuntimeException("Cannot generate Rover Campaigns intent when Rover Campaigns is not initialized.")
                    )
                    .resolveSingletonOrFail(DateFormattingInterface::class.java)
            )

            return PendingIntent.getActivity(
                context,
                // use hashcode on the ID string (itself a UUID, which is bigger than 32 bits alas)
                // as a way of keeping the separate PendingIntents actually separate.  chance of
                // collision is not too high.
                notification.id.hashCode(),
                Intent(
                    context,
                    TransientNotificationLaunchActivity::class.java
                ).apply {
                    putExtra(
                        NOTIFICATION_JSON,
                        notificationJson.toString()
                    )
                },
                PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { FLAG_IMMUTABLE } else { 0 }
            )
        }

        private const val NOTIFICATION_JSON = "notification_json"
    }
}
