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

import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Build
import io.rover.sdk.core.Rover
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.routing.startActivityOrOpenApp
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.graphql.decodeJson
import io.rover.sdk.notifications.graphql.encodeJson
import io.rover.sdk.notifications.ui.concerns.NotificationStoreInterface
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
class TransientNotificationLaunchActivity : Activity() {

    // TODO: make transparent/invisible somehow to avoid flicker

    override fun onStart() {
        super.onStart()

        // A notification tap can cold-start the app, so Rover may not have been initialized yet.
        // Bail out quietly rather than throwing out of onStart and taking the host app down.
        val rover = Rover.failableShared
        if (rover == null) {
            log.e("Rover is not initialized, so this notification cannot be opened.  Ensure Rover.initialize() is called in Application.onCreate().")
            finish()
            return
        }

        val notificationOpen = rover.resolve(NotificationOpenInterface::class.java)
        if (notificationOpen == null) {
            log.e("Could not resolve NotificationOpenInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            finish()
            return
        }
        val influenceTracker = rover.resolve(InfluenceTrackerServiceInterface::class.java)
        if (influenceTracker == null) {
            log.e("Could not resolve InfluenceTrackerServiceInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            finish()
            return
        }
        val dateFormatting = rover.resolve(DateFormattingInterface::class.java)
        if (dateFormatting == null) {
            log.e("Could not resolve DateFormattingInterface in Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
            finish()
            return
        }
        val notificationsRepository = rover.resolve(NotificationStoreInterface::class.java)
        if (notificationsRepository == null) {
            log.e("Could not resolve NotificationsRepositoryInterface in the Rover container.  Ensure NotificationAssembler() is added to Rover.initialize.")
        }

        log.v("Transient notification launch activity running.")

        val notificationJson = intent.getStringExtra(NOTIFICATION_JSON)
        if (notificationJson == null) {
            log.w("No notification json passed, finishing activity")
            finish()
            return
        }

        // this will also do the side-effect of issuing the Notification Opened event, which
        // is the whole reason for this activity existing.

        val notification = try {
            Notification.decodeJson(JSONObject(notificationJson), dateFormatting)
        } catch (exception: Exception) {
            log.w("Badly formed notification, could not mark it as read: $exception, at ${exception.stackTraceToString()}")
            null
        }
        if (notification != null) {
            try {
                notificationsRepository?.markRead(notification)
            } catch (exception: Exception) {
                log.w("Could not mark notification as read: $exception, at ${exception.stackTraceToString()}")
            }
        }

        influenceTracker.notificationOpenedDirectly()

        val intent = notificationOpen.intentForOpeningNotificationFromJson(notificationJson)
        if (intent == null) {
            log.w("Notification did not produce an intent; finishing launch activity.")
            finish()
            return
        }

        // This activity is the only one in its task, so finishing without launching anything
        // takes the task with it and the app vanishes from the user's point of view. When the
        // intent cannot be handled, open the app instead so the tap lands somewhere.
        //
        // Routing may have queued Hub navigation for this tap. It is left in place: if the app
        // opens, the Hub consumes it; if it does not, a stale destination is accepted rather
        // than undone here. Queueing at dispatch time instead is tracked as SDK-379.
        startActivityOrOpenApp(
            intent = intent,
            openApp = rover.resolve(Intent::class.java, "openApp"),
            failureMessage = "No activity could be found to handle the Intent needed to start the notification.\n" +
                "This could be because the deep link scheme slug you set on NotificationsAssembler does not match what is set in your Rover account, or that an Activity is missing from your manifest.",
        )

        finish()
    }

    companion object {
        fun generateLaunchIntent(
            context: Context,
            notification: Notification
        ): PendingIntent {
            val notificationJson = notification.encodeJson(
                (
                    Rover.shared
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
