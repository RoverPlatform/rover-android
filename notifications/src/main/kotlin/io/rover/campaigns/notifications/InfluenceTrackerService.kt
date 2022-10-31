package io.rover.campaigns.notifications

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.rover.campaigns.core.logging.log
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.platform.whenNotNull
import io.rover.campaigns.notifications.domain.Notification
import io.rover.campaigns.notifications.graphql.decodeJson
import io.rover.campaigns.notifications.graphql.encodeJson
import org.json.JSONException
import org.json.JSONObject

class InfluenceTrackerService(
    private val application: Application,
    localStorage: LocalStorage,
    private val dateFormatting: DateFormattingInterface,
    private val notificationOpen: NotificationOpenInterface,
    private val lifecycle: Lifecycle,
    private val influenceThresholdSeconds: Int = 60
) : InfluenceTrackerServiceInterface {
    private val store = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

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
        log.v("Marked that a notification arrived not intended for Rover Campaigns, so forgetting current influenced-open candidate.")
    }

    private var notificationJustOpened = false

    override fun notificationOpenedDirectly() {
        notificationJustOpened = true
    }

    override fun startListening() {
        // goal is to notice when:
        // * app is opened or switched back to;
        // * but NOT opened from a notification or a navigation event within the app.

        lifecycle.addObserver(
            object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                fun onResume() {
                    if (!notificationJustOpened) {
                        // app was switched to but not by opening a notification.

                        // thus, we can track an influenced open here.
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
                                notificationJustOpened = false
                                return
                            }

                            notificationOpen.appOpenedAfterReceivingNotification(
                                notification
                            )
                        }
                    }

                    lastSeenNotificationAt = null
                    lastSeenNotificationJson = null
                    notificationJustOpened = false
                }
            }
        )
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "influenced-opens"
    }
}