package io.rover.notifications

import io.rover.core.data.NetworkResult
import io.rover.core.data.domain.AttributeValue
import io.rover.core.logging.log
import io.rover.core.streams.*
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.LocalStorage
import io.rover.core.platform.whenNotNull
import io.rover.notifications.domain.Notification
import io.rover.core.data.graphql.getObjectIterable
import io.rover.notifications.graphql.decodeJson
import io.rover.notifications.graphql.encodeJson
import io.rover.core.data.state.StateManagerServiceInterface
import io.rover.core.events.EventQueueService
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.platform.merge
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.util.concurrent.Executor

/**
 * Responsible for reactively persisting notifications and informing subscribers of changes.
 *
 * Must be a singleton, because changes dispatch updates to all subscribers as a side-effect.
 */
class NotificationsRepository(
    private val dateFormatting: DateFormattingInterface,
    private val ioExecutor: Executor,
    private val mainThreadScheduler: Scheduler,
    private val eventQueue: EventQueueServiceInterface,
    private val stateManagerService: StateManagerServiceInterface,
    localStorage: LocalStorage
): NotificationsRepositoryInterface {
    // TODO: gate access to the localStorage via a single-thread executor pool.

    // TODO: break this object up into: Notifications Remote API, possibly make StateStore have a
    // subscriber model after all, in which consumers would provide their query fragment at
    // registration time, removing the afterAssembly() callback wire-up.

    override fun updates(): Publisher<NotificationsRepositoryInterface.Emission.Update> = Publishers.concat(
        currentNotificationsOnDisk().filterNulls().map { existingNotifications ->
            NotificationsRepositoryInterface.Emission.Update(existingNotifications)
        },
        epic
    ).observeOn(mainThreadScheduler).filterForSubtype()

    override fun events(): Publisher<NotificationsRepositoryInterface.Emission.Event> = epic.filterForSubtype()

    override fun refresh() {
        actions.onNext(Action.Refresh())
    }

    override fun markRead(notification: Notification) {
        actions.onNext(Action.MarkRead(notification))
    }

    override fun delete(notification: Notification) {
        actions.onNext(Action.MarkDeleted(notification))
    }

    override fun notificationArrivedByPush(notification: Notification) {
        actions.onNext(Action.NotificationArrivedByPush(notification))
    }

    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private val actions = PublishSubject<Action>()

    private fun currentNotificationsOnDisk(): Publisher<List<Notification>?> {
        return Publishers.defer {
            Publishers.just(
                    try {
                        keyValueStorage[STORE_KEY].whenNotNull { jsonString ->
                            JSONArray(jsonString).getObjectIterable().map { notificationJson ->
                                Notification.decodeJson(notificationJson, dateFormatting)
                            }
                        }
                    } catch (e: JSONException) {
                        log.w("Invalid JSON appeared in Notifications cache, so starting fresh: ${e.message}")
                        null
                    }
                )
        }.subscribeOn(ioExecutor)
    }

    /**
     * Add the existing notifications on disk together any new ones.
     *
     * The new list need not be exhaustive; existing non-conflicting records will be kept,
     * up until a cutoff.  That means this method may be used for partial updates.
     */
    private fun mergeWithLocalStorage(incomingNotifications: List<Notification>): Publisher<out List<Notification>> {
        return currentNotificationsOnDisk().map { notifications ->

            val notificationsOnDiskById = notifications?.associateBy { it.id } ?: hashMapOf()
            val incomingNotificationsById = incomingNotifications.associateBy { it.id }

            notificationsOnDiskById.merge(incomingNotificationsById) { existing, incoming ->
                incoming.copy(
                    isRead = incoming.isRead || existing.isRead,
                    isDeleted = incoming.isDeleted || existing.isDeleted
                )
            }.values.orderNotifications().take(MAX_NOTIFICATIONS_LIMIT)
        }.subscribeOn(ioExecutor).doOnNext { log.v("Merge result: $it") }
    }

    // a rule: things that touch external stuff by I/O must be publishers.  flat functions are only
    // allowed if they are pure.

    private fun replaceLocalStorage(notifications: List<Notification>): Publisher<out List<Notification>> {
        return Publishers.defer {
            log.v("Updating local storage with ${notifications.size} notifications.")
            keyValueStorage[STORE_KEY] = JSONArray(notifications.map { it.encodeJson(dateFormatting) }).toString()
            Publishers.just(notifications)
        }.subscribeOn(ioExecutor)
    }

    private val queryFragment: String = """
        notifications {
          ...notificationFields
        }
    """

    /**
     * This chain of behaviour maps incoming updates from the [StateManagerServiceInterface] to
     * notifications updates, along with the side-effect of updating local state.
     */
    private val stateStoreObserverChain = stateManagerService.updatesForQueryFragment(
        queryFragment,
        listOf("notificationFields")
    ).flatMap { networkResult ->
        when(networkResult) {
            is NetworkResult.Error -> {
                Publishers.concat(
                    Publishers.just(NotificationsRepositoryInterface.Emission.Event.Refreshing(false)),
                    Publishers.just(NotificationsRepositoryInterface.Emission.Event.FetchFailure(networkResult.throwable.message ?: "Unknown")),
                    this.currentNotificationsOnDisk().map {
                        // just yield the current notifications on disk, if any.
                        NotificationsRepositoryInterface.Emission.Update(it ?: emptyList())
                    }
                )
            }
            is NetworkResult.Success -> {
                try {
                    val newNotifications = decodeNotificationsPayload(networkResult.response)
                    Publishers.concat(
                        Publishers.just(
                            NotificationsRepositoryInterface.Emission.Event.Refreshing(false)
                        ),
                        mergeWithLocalStorage(newNotifications)
                            .flatMap { notifications ->
                                // side-effect: update local storage!
                                replaceLocalStorage(notifications).map {
                                    NotificationsRepositoryInterface.Emission.Update(it)
                                }
                            }
                    )
                } catch (jsonError: JSONException) {
                    Publishers.concat(
                        Publishers.just(NotificationsRepositoryInterface.Emission.Event.Refreshing(false)),
                        Publishers.just(NotificationsRepositoryInterface.Emission.Event.FetchFailure(jsonError.message ?: "Unknown"))
                    )
                }
            }
        }
    }

    private val epic: Publisher<NotificationsRepositoryInterface.Emission> =
        Publishers.merge(
            actions.flatMap { action ->
                when (action) {
                    is Action.Refresh -> {
                        Publishers.concat(
                            Publishers.just(NotificationsRepositoryInterface.Emission.Event.Refreshing(true))
                                .doOnComplete {
                                    log.v("Triggering Device State Manager refresh.")
                                    stateManagerService.triggerRefresh()

                                    // this will result in an emission being received by the state
                                    // manager updates observer.
                                }
                        )
                    }
                    is Action.MarkDeleted -> {
                        doMarkAsDeleted(action.notification).map { NotificationsRepositoryInterface.Emission.Update(it) }
                    }
                    is Action.MarkRead -> {
                        doMarkAsRead(action.notification).map { NotificationsRepositoryInterface.Emission.Update(it) }
                    }
                    is Action.NotificationArrivedByPush -> {
                        mergeWithLocalStorage(listOf(action.notification)).flatMap { merged -> replaceLocalStorage(merged) }.map {
                            NotificationsRepositoryInterface.Emission.Update(it)
                        }
                    }
                }
            },
            stateStoreObserverChain
        ).observeOn(mainThreadScheduler)
        .shareHotAndReplay(0)

    /**
     * When subscribed, performs the side-effect of marking the given notification as deleted
     * locally (on the I/O executor).  If successful, it will yield an emission appropriate
     * to inform consumers of the change.
     */
    private fun doMarkAsDeleted(notification: Notification): Publisher<List<Notification>> {
        return currentNotificationsOnDisk().flatMap { onDisk ->
            if(onDisk == null) {
                log.w("No notifications currently stored on disk.  Cannot mark notification as deleted.")
                return@flatMap Publishers.empty<List<Notification>>()
            }

            val alreadyDeleted = onDisk.find { it.id == notification.id }?.isDeleted ?: false

            val modified = onDisk.map { onDiskNotification ->
                if(onDiskNotification.id == notification.id) {
                    onDiskNotification.copy(isDeleted = true)
                } else onDiskNotification
            }

            if(!alreadyDeleted) {
                eventQueue.trackEvent(
                    Event(
                        "Notification Marked Deleted",
                        hashMapOf(
                            Pair("NotificationID", AttributeValue.String(notification.id))
                        )
                    ),
                    EventQueueService.ROVER_NAMESPACE
                )
            }

            replaceLocalStorage(
                modified
            ).map { modified }
        }.subscribeOn(ioExecutor)
    }

    /**
     * When subscribed, performs the side-effect of marking the given notification as deleted
     * locally (on the I/O executor).
     */
    private fun doMarkAsRead(notification: Notification): Publisher<List<Notification>> {
        return currentNotificationsOnDisk().flatMap { onDisk ->
            if(onDisk == null) {
                log.w("No notifications currently stored on disk.  Cannot mark notification as read.")
                return@flatMap Publishers.empty<List<Notification>>()
            }

            val alreadyRead = onDisk.find { it.id == notification.id }?.isRead ?: false

            val modified = onDisk.map { onDiskNotification ->
                if(onDiskNotification.id == notification.id) {
                    onDiskNotification.copy(isRead = true)
                } else onDiskNotification
            }

            if(!alreadyRead) {
                eventQueue.trackEvent(
                    Event(
                        "Notification Marked Read",
                        hashMapOf(
                            Pair("notificationID", AttributeValue.String(notification.id))
                        )
                    ),
                    EventQueueService.ROVER_NAMESPACE
                )
            }

            replaceLocalStorage(
                modified
            ).map { modified }
        }.subscribeOn(ioExecutor)
    }

    private fun Collection<Notification>.orderNotifications(): List<Notification> {
        return this
            .sortedByDescending { it.deliveredAt }
    }

    @Throws(JSONException::class)
    private fun decodeNotificationsPayload(data: JSONObject): List<Notification> =
        data.getJSONArray("notifications").getObjectIterable().map {
            notificationJson -> Notification.decodeJson(notificationJson, dateFormatting)
        }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.rover.notification-storage"
        private const val STORE_KEY = "local-notifications-cache"

        private const val MAX_NOTIFICATIONS_LIMIT = 100
    }


    sealed class Action {
        /**
         * User has requested a refresh.
         */
        class Refresh: Action()

        /**
         * User has requested a refresh.
         */
        class MarkRead(val notification: Notification): Action()

        /**
         * User has requested a mark to be delete.
         */
        class MarkDeleted(val notification: Notification): Action()

        /**
         * A notification arrived by push.  This will add it to the repository.
         */
        class NotificationArrivedByPush(val notification: Notification): Action()
    }
}
