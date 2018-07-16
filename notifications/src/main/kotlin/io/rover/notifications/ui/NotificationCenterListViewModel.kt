package io.rover.notifications.ui

import io.rover.core.logging.log
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import org.reactivestreams.Publisher
import io.rover.core.streams.doOnNext
import io.rover.core.streams.doOnRequest
import io.rover.core.streams.filterNulls
import io.rover.core.streams.map
import io.rover.core.streams.share
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.tracking.SessionTrackerInterface
import io.rover.notifications.domain.Notification
import io.rover.notifications.ui.concerns.NotificationCenterListViewModelInterface
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
import io.rover.core.streams.distinctUntilChanged
import io.rover.core.streams.subscribe
import java.util.Date

class NotificationCenterListViewModel(
    private val notificationsRepository: NotificationsRepositoryInterface,
    private val sessionTracker: SessionTrackerInterface
): NotificationCenterListViewModelInterface {
    override fun events(): Publisher<out NotificationCenterListViewModelInterface.Event> = epic.doOnRequest {
        // Infer from a new subscriber that it's a newly displayed view, and, thus, an
        // automatic refresh should be kicked off.
        requestRefresh()
    }

    override fun notificationClicked(notification: Notification) {
        actions.onNext(Action.NotificationClicked(notification))
    }

    override fun deleteNotification(notification: Notification) {
        actions.onNext(Action.DeleteNotification(notification))
    }

    override fun requestRefresh() {
        notificationsRepository.refresh()
    }

    // State: stable IDs mapping.  Ensure that we have a 100% consistent stable ID for the lifetime
    // of the view model (which will sufficiently match the lifetime of the recyclerview that
    // requires the stableids).
    private var highestStableId = 0
    private val stableIds: MutableMap<String, Int> = mutableMapOf()

    private val actions = PublishSubject<Action>()

    private val epic: Publisher<NotificationCenterListViewModelInterface.Event> =
        Publishers.merge(
            actions.share().map { action ->
                when(action) {
                    is Action.NotificationClicked -> {
                        // the delete operation is entirely asynchronous, as a side-effect.
                        notificationsRepository.markRead(action.notification)

                        NotificationCenterListViewModelInterface.Event.Navigate(
                            action.notification
                        )
                    }

                    is Action.DeleteNotification -> {
                        notificationsRepository.delete(action.notification)
                        null
                    }
                }
            }.filterNulls(),
            notificationsRepository.updates().map { update ->
                update
                    .notifications
                    .filter { !it.isDeleted }
                    .filter { it.expiresAt?.after(Date()) ?: true }
            }.doOnNext { notificationsReadyForDisplay ->
                // side-effect, update the stable ids list map:
                updateStableIds(notificationsReadyForDisplay)
            }.map { notificationsReadyForDisplay ->
                NotificationCenterListViewModelInterface.Event.ListUpdated(
                    notificationsReadyForDisplay,
                    stableIds
                )
            }.doOnNext { updateStableIds(it.notifications) },
            notificationsRepository
                .events()
                .map { repositoryEvent ->
                    log.v("Received event $repositoryEvent")
                when(repositoryEvent) {
                    is NotificationsRepositoryInterface.Emission.Event.Refreshing -> {
                        NotificationCenterListViewModelInterface.Event.Refreshing(repositoryEvent.refreshing)
                    }
                    is NotificationsRepositoryInterface.Emission.Event.FetchFailure -> {
                        NotificationCenterListViewModelInterface.Event.DisplayProblemMessage()
                    }
                }
            }
        ).shareHotAndReplay(0)

    override fun becameVisible() {
        visibilityStateSubject.onNext(true)
    }

    override fun becameInvisible() {
        visibilityStateSubject.onNext(false)
    }

    protected fun trackEnterNotificationCenter() {
        sessionTracker.enterSession(
            "NotificationCenter",
            "Notification Center Presented",
            "Notification Center Viewed",
            hashMapOf()
        )
    }

    protected fun trackLeaveNotificationCenter() {
        sessionTracker.leaveSession(
            "NotificationCenter",
            "Notification Center Dismissed",
            hashMapOf()
        )
    }

    private fun updateStableIds(notifications: List<Notification>) {
        notifications.forEach { notification ->
            if(!stableIds.containsKey(notification.id)) {
                stableIds[notification.id] = ++highestStableId
            }
        }
    }

    private val visibilityStateSubject = PublishSubject<Boolean>()

    init {
        visibilityStateSubject.distinctUntilChanged().subscribe { visible ->
            if(visible) {
                trackEnterNotificationCenter()
            } else {
                trackLeaveNotificationCenter()
            }
        }
    }

    private sealed class Action {
        data class NotificationClicked(val notification: Notification): Action()
        data class DeleteNotification(val notification: Notification): Action()
    }
}
