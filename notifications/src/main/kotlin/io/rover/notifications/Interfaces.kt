package io.rover.notifications

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import io.rover.notifications.domain.Notification

interface PushReceiverInterface {
    /**
     * You need to implement a
     * [FirebaseMessagingService](https://firebase.google.com/docs/reference/android/com/google/firebase/messaging/FirebaseMessagingService)
     * in your application, and then override its `onMessageReceived` method.
     *
     * If you are using GCM instead of FCM, then look at [onMessageReceivedDataAsBundle] instead.
     *
     * Then, retrieve `data` from the `RemoteMessage` object it received and pass it here.
     *
     * In response, the Push Receiver will build an appropriate notification and add it to the Android
     * notification area (although note this will not be called for every notification sent to users
     * on behalf of your application; if the Rover Cloud determined that a Firebase Display Message
     * was sufficient to display the push, then this callback may not happen at all unless the app
     * is in the foreground).
     *
     * In Kotlin, it may look something like this:
     *
     * ```
     * class MyAppCustomFirebaseReceiver: FirebaseMessagingService() {
     *     override fun onMessageReceived(remoteMessage: RemoteMessage) {
     *         val pushReceiver = Rover.sharedInstance.pushReceiver
     *         pushReceiver.onMessageReceivedData(remoteMessage.data)
     *     }
     * }
     * ```
     */
    fun onMessageReceivedData(parameters: Map<String, String>)

    /**
     * Equivalent to [onMessageReceivedData], but accepts a Bundle instead of a Map.
     *
     * This version is appropriate for use with GCM in lieu of FCM.  See `README.legacy-gcm.md` for
     * details.
     */
    fun onMessageReceivedDataAsBundle(parameters: Bundle)

    /**
     * Handle an already decoded Rover [Notification].
     */
    @Deprecated("Will be moved elsewhere")
    fun onMessageReceivedNotification(notification: Notification)

    /**
     * You need to implement a
     * [FirebaseInstanceIdService](https://firebase.google.com/docs/reference/android/com/google/firebase/iid/FirebaseInstanceIdService)
     * in your application, and then override its `onTokenRefresh` method.
     *
     * Then, pass the token it received here.  Thread safe; you may call this from whatever thread
     * you like.
     *
     * Then the Rover SDK will be able to register that push token to receive Rover pushes.  If this
     * step is omitted, then the application will never receive any Rover-powered push
     * notifications.
     */
    fun onTokenRefresh(token: String?)
}

interface NotificationOpenInterface {
    /**
     * A pending intent that will be used for the Android notification itself.
     *
     * Will return a [PendingIntent] suitable for use as an Android notification target that will
     * launch the [TransientNotificationLaunchActivity] to start up and actually execute the
     * notification's action.
     */
    fun pendingIntentForAndroidNotification(notification: Notification): PendingIntent

    /**
     * Return an intent for opening a notification from the Android notification drawer. This is
     * called by the transient notification launch activity to replace itself with a new stack.
     */
    fun intentForOpeningNotificationFromJson(notificationJson: String): Intent

    /**
     * Return an intent for directly opening the notification within the app.
     *
     * Note: if you wish to override the intent creation logic, instead considering overriding
     * [TopLevelNavigation] or [ActionRoutingBehaviour].
     *
     * Returns null if no intent is appropriate.
     */
    fun intentForOpeningNotificationDirectly(notification: Notification): Intent?

    /**
     * Should be called when the application is opened soon after a notification is received.
     */
    fun appOpenedAfterReceivingNotification(notification: Notification)
}

interface InfluenceTrackerServiceInterface {
    /**
     * Start monitoring the application lifecycle for app opens, allowing the Influence Tracker
     * to emit an Event when the app is opened soon after a push notification arrives.
     */
    fun startListening()

    /**
     * Let the influence tracker know that a notification has been received.
     */
    fun notifyNotificationReceived(
        notification: Notification
    )

    /**
     * Let the influence tracker know that a push notification not associated with Rover has been
     * received, and so therefore an influenced open should not be tracked in order to avoid
     * tracking credit for an influenced open for a Rover campaign that was not actually the most
     * recently received push.
     */
    fun nonRoverPushReceived()

    /**
     * Let the influence tracker know that a Rover push notification has just been opened,
     * so the influence tracker should not count the app coming to the foreground as
     * being an influenced open (it will be instead tracked elsewhere as a direct open).
     */
    fun notificationOpenedDirectly()
}
