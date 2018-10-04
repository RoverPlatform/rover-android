package io.rover.notifications

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import io.rover.core.logging.log
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.LocalStorage
import io.rover.core.platform.whenNotNull
import io.rover.notifications.domain.Notification
import io.rover.notifications.graphql.decodeJson
import io.rover.notifications.graphql.encodeJson
import org.json.JSONException
import org.json.JSONObject

class InfluenceTrackerService(
    private val application: Application,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface,
    private val notificationOpen: NotificationOpenInterface,
    private val influenceThresholdSeconds: Int = 60
) : InfluenceTrackerServiceInterface {
    private val store = localStorage.getKeyValueStorageFor("influenced-opens")

    private var lastSeenNotificationAt: Long?
        get() = try { store["last-seen-notification-at"]?.toLong() } catch (numberFormatException: NumberFormatException) { null }
        set(value) {
            if (value == null) {
                store.unset("last-seen-notification-at")
            } else {
                store["last-seen-notification-at"] = value.toString()
            }
        }

    private var lastSeenNotificationJson: String?
        get() = store["last-seen-notification"]
        set(value) {
            if (value == null) {
                store.unset("last-seen-notification")
            } else {
                store["last-seen-notification"] = value
            }
        }

    override fun notifyNotificationReceived(notification: Notification) {
        // store current time and the notification itself so they may be checked when app is opened.
        lastSeenNotificationAt = System.currentTimeMillis() / 1000L
        lastSeenNotificationJson = notification.encodeJson(dateFormatting).toString()
        log.v("Marked that a notification arrived.")
    }

    override fun nonRoverPushReceived() {
        lastSeenNotificationAt = null
        lastSeenNotificationJson = null
        log.v("Marked that a non-Rover notification arrived, so forgetting current influenced-open candidate.")
    }

    override fun startListening() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(activity: Activity?) { }

                override fun onActivityResumed(activity: Activity?) { }

                override fun onActivityStarted(activity: Activity?) { }

                override fun onActivityDestroyed(activity: Activity?) { }

                override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) { }

                override fun onActivityStopped(activity: Activity?) { }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    if (savedInstanceState == null && activity.intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                        // app was started from the launcher by tapping the icon. contrasted with
                        // navigating within the app, state restore happening when returning to the
                        // app after process death, or app being opened by tapping on a
                        // notification.

                        // thus, we can track an influenced opens here.
                        val seenWithinThreshold = lastSeenNotificationAt?.whenNotNull { lastSeen ->
                            System.currentTimeMillis() / 1000L - lastSeen < influenceThresholdSeconds
                        }

                        val capturedLastSeenNotificationJson = lastSeenNotificationJson

                        if (seenWithinThreshold == true && capturedLastSeenNotificationJson != null) {
                            log.v("App open influenced by a notification detected.")

                            // decode it
                            val notification = try {
                                Notification.decodeJson(
                                    JSONObject(capturedLastSeenNotificationJson),
                                    dateFormatting
                                )
                            } catch (e: JSONException) {
                                log.w("Invalid JSON for a Notification appeared in storage for tracking influenced opens.  Dropping. Reason: ${e.message}")
                                lastSeenNotificationAt = null
                                lastSeenNotificationJson = null
                                return
                            }

                            notificationOpen.appOpenedAfterReceivingNotification(
                                notification
                            )

                            lastSeenNotificationAt = null
                            lastSeenNotificationJson = null
                        }
                    }
                }
            }
        )
    }
}