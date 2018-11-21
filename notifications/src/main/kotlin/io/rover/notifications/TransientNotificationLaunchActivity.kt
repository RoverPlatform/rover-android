package io.rover.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import io.rover.core.Rover
import io.rover.core.logging.log
import io.rover.core.platform.DateFormattingInterface
import io.rover.notifications.domain.Notification
import io.rover.notifications.graphql.decodeJson
import io.rover.notifications.graphql.encodeJson
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
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

        val rover = Rover.shared
        if(rover == null) {
            log.e("TransientNotificationLaunchActivity cannot be used before Rover is initialized.")
            return
        }
        val notificationOpen = rover.resolve(NotificationOpenInterface::class.java)
        if(notificationOpen == null) {
            log.e("Could not resolve NotificationOpenInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            return
        }
        val influenceTracker = rover.resolve(InfluenceTrackerServiceInterface::class.java)
        if(influenceTracker == null) {
            log.e("Could not resolve InfluenceTrackerServiceInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            return
        }
        val dateFormatting = rover.resolve(DateFormattingInterface::class.java)
        if(dateFormatting == null) {
            log.e("Could not resolve DateFormattingInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            return
        }
        val notificationsRepository = rover.resolve(NotificationsRepositoryInterface::class.java)
        if (notificationsRepository == null) {
            log.e("Could not resolve NotificationsRepositoryInterface in the Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
        }

        log.v("Transient notification launch activity running.")

        // grab the notification back out of the arguments.
        val notificationJson = this.intent.extras.getString(NOTIFICATION_JSON)

        // this will also do the side-effect of issuing the Notification Opened event, which
        // is the whole reason for this activity existing.

        val intent = notificationOpen.intentForOpeningNotificationFromJson(notificationJson)

        if (intent.resolveActivityInfo(this.packageManager, PackageManager.GET_SHARED_LIBRARY_FILES) == null) {
            log.e(
                "No activity could be found to handle the Intent needed to start the notification.\n" +
                    "This could be because the deep link scheme slug you set on NotificationsAssembler does not match what is set in your Rover account, or that an Activity is missing from your manifest.\n\n" +
                    "Intent was: $intent"
            )
            finish()
            return
        }

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

        ContextCompat.startActivity(
            this,
            intent,
            null
        )

        finish()
    }

    companion object {
        fun generateLaunchIntent(
            context: Context,
            notification: Notification
        ): PendingIntent {

            val notificationJson = notification.encodeJson(
                (Rover.shared ?: throw RuntimeException("Cannot generate Rover intent when Rover is not initialized."))
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
                        NOTIFICATION_JSON, notificationJson.toString()
                    )
                },
                PendingIntent.FLAG_ONE_SHOT
            )
        }

        private const val NOTIFICATION_JSON = "notification_json"
    }
}