package io.rover.notifications.ui.concerns

import android.graphics.Bitmap
import org.reactivestreams.Publisher
import io.rover.core.ui.concerns.BindableViewModel
import io.rover.notifications.domain.Notification

/**
 * This repository syncs and stores the Push Notifications.
 *
 * A per-device list of received push notifications is kept on the Rover Cloud API (although note
 * this is device-bound, and not bound to any sort of per-user account).  We just use SharedPreferences
 * to store a JSON-encoded blob of all of the current notifications.  This is sufficient because we
 * never anticipate storing more than 100.
 *
 * The arrangement is rather CQRS-like; the notifications list coming from the cloud API is
 * read-only, any items in it can only be marked as read or deleted (the only possible mutations)
 * not through the GraphQL API but instead only by asynchronously emitted events that may be applied
 * any arbitrary amount of time in the future.  This Repository is responsible for maintaining its
 * own state for read and deleted.
 */
interface NotificationsRepositoryInterface {
    /**
     * Obtain the list of push notifications received by this device (and that were marked for
     * storage in the Notification Center).
     *
     * A refresh is triggered when subscribed.
     */
    fun updates(): Publisher<Emission.Update>

    /**
     * Subscribe to this to be notified of the current count of unread messages.
     */
    fun unreadCount(): Publisher<Int>

    /**
     * Be informed of changes occurring to the Repository itself; for instance, if a refresh cycle
     * currently running.
     */
    fun events(): Publisher<Emission.Event>

    /**
     * Manually trigger a refresh.
     */
    fun refresh()

    /**
     * Request that the notification be as marked as read.  This method will return immediately.
     * The consumer will see the changes by a new [Emission.Update] being updated on the [updates]
     * publisher.
     */
    fun markRead(notification: Notification)

    /**
     * Request that all notifications currently in the repository are marked as read.
     */
    fun markAllAsRead()

    /**
     * A notification arrived by push.  This will asynchronously insert it into the repository. This
     * method will return immediately.
     */
    fun notificationArrivedByPush(notification: Notification)

    /**
     * Request that the notification be marked as deleted.  This method will return immediately.
     * The consumer will see the changes by a new [Emission.Update] being updated on the [updates]
     * publisher.
     */
    fun delete(notification: Notification)

    fun mergeRetrievedNotifications(notifications: List<Notification>)

    sealed class Emission {
        sealed class Event : Emission() {
            data class Refreshing(val refreshing: Boolean) : Event()
            data class FetchFailure(val reason: String) : Event()
        }

        data class Update(val notifications: List<Notification>) : Emission()
    }
}

interface NotificationCenterListViewModelInterface : BindableViewModel {
    /**
     * Subscribe to this event stream to be informed of when a user performs an action that needs
     * to be handled.
     */
    fun events(): Publisher<out Event>

    sealed class Event {
        /**
         * The list has changed.  Gives you the complete list of notifications to be displayed, but
         * also a map for looking up stable integer ids for RecyclerView's use.  Those stable IDs
         * are guaranteed to remain consistent over the lifetime of the view model.
         *
         * Note that the IDs of notifications themselves are guaranteed to be stable, so they should
         * be used to perform a differential update (which RecyclerView supports).
         *
         * If the list is empty, display the empty view.
         *
         * Note: unusually for an MVVM UI pattern, this is exposing the [Notification] domain model
         * object directly.
         *
         * This is to better suit View implementations that may display any arbitrary detail of the
         * Notification.  Notification itself is a value object.
        */
        data class ListUpdated(val notifications: List<Notification>, val stableIds: Map<String, Int>) : Event()

        data class Navigate(val notification: Notification) : Event()

        /**
         * The backing data store is in the process of starting or stopping a refresh operation. The
         * consumer may use this event to indicate that a refresh is running.
         */
        data class Refreshing(val refreshing: Boolean) : Event()

        class DisplayProblemMessage : Event()
    }

    /**
     * Emit the appropriate Event needed for this notification being clicked.
     */
    fun notificationClicked(notification: Notification)

    /**
     * Attempt to delete the notification from the Rover cloud device state API, and also
     * mark it as deleted in the local store.
     */
    fun deleteNotification(notification: Notification)

    /**
     * User did the pull down gesture to ask for a refresh.
     */
    fun requestRefresh()
}

/**
 * View model for notification list items in the notification center.
 */
interface NotificationItemViewModelInterface : BindableViewModel {
    /**
     * Indicates whether the view should should show an image thumbnail area.
     */
    val showThumbnailArea: Boolean

    /**
     *
     * (Note: normally with a view model design, one would avoid exposing the domain object
     * to the view. In this case, to allow for more convenient extensibility, we expose it.
     */
    val notificationForDisplay: Notification

    /**
     * Get the image that should be displayed in the thumbnail area of a notification list item, if
     * the Notification has Rich Media image attachment.
     *
     * It should be displayed with "Fill"-style scaling.
     */
    fun requestThumbnailImage(): Publisher<Bitmap>
}