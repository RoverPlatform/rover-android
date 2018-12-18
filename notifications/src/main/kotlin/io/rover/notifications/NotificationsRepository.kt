package io.rover.notifications

import io.rover.core.data.domain.AttributeValue
import io.rover.core.data.graphql.getObjectIterable
import io.rover.core.data.sync.GraphQLResponse
import io.rover.core.data.sync.SyncCoordinatorInterface
import io.rover.core.data.sync.SyncDecoder
import io.rover.core.data.sync.SyncQuery
import io.rover.core.data.sync.SyncRequest
import io.rover.core.data.sync.SyncResource
import io.rover.core.data.sync.last
import io.rover.core.events.EventQueueService
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.logging.log
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.DeviceIdentificationInterface
import io.rover.core.platform.LocalStorage
import io.rover.core.platform.merge
import io.rover.core.platform.whenNotNull
import io.rover.core.streams.PublishSubject
import io.rover.core.streams.Publishers
import io.rover.core.streams.Scheduler
import io.rover.core.streams.asPublisher
import io.rover.core.streams.doOnComplete
import io.rover.core.streams.doOnNext
import io.rover.core.streams.filterForSubtype
import io.rover.core.streams.filterNulls
import io.rover.core.streams.flatMap
import io.rover.core.streams.map
import io.rover.core.streams.observeOn
import io.rover.core.streams.shareHotAndReplay
import io.rover.core.streams.subscribeOn
import io.rover.notifications.domain.Notification
import io.rover.notifications.domain.events.asAttributeValue
import io.rover.notifications.graphql.decodeJson
import io.rover.notifications.graphql.encodeJson
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
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
    private val syncCoordinator: SyncCoordinatorInterface,
    localStorage: LocalStorage
) : NotificationsRepositoryInterface {
    override fun updates(): Publisher<NotificationsRepositoryInterface.Emission.Update> = Publishers.concat(
        currentNotificationsOnDisk().map { existingNotifications ->
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
        actions.onNext(Action.NotificationsFetchedOrArrived(listOf(notification)))
    }

    override fun mergeRetrievedNotifications(notifications: List<Notification>) {
        actions.onNext(Action.NotificationsFetchedOrArrived(notifications))
    }

    private val keyValueStorage = localStorage.getKeyValueStorageFor(STORAGE_CONTEXT_IDENTIFIER)

    private val actions = PublishSubject<Action>()

    private fun currentNotificationsOnDisk(): Publisher<List<Notification>> {
        return Publishers.defer {
            Publishers.just(
                    try {
                        keyValueStorage[STORE_KEY].whenNotNull { jsonString ->
                            JSONArray(jsonString).getObjectIterable().map { notificationJson ->
                                Notification.decodeJson(notificationJson, dateFormatting)
                            }
                        } ?: listOf()
                    } catch (e: JSONException) {
                        log.w("Invalid JSON appeared in Notifications cache, so starting fresh: ${e.message}")
                        listOf<Notification>()
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

    /**
     * This chain of behaviour maps incoming updates from the [StateManagerServiceInterface] to
     * notifications updates, along with the side-effect of updating local state.
     */
    private val stateStoreObserverChain = syncCoordinator.syncResults.flatMap { syncResult ->
        log.v("Received sync completed notification.")
        when(syncResult) {
            SyncCoordinatorInterface.Result.Succeeded -> Publishers.just(NotificationsRepositoryInterface.Emission.Event.Refreshing(false))
            else -> {
                listOf(
                    NotificationsRepositoryInterface.Emission.Event.Refreshing(false),
                    NotificationsRepositoryInterface.Emission.Event.FetchFailure("Sync failed.")
                ).asPublisher()
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
                                    log.v("Triggering Sync Coordinator refresh by request.")
                                    // this will result in an emission being received by the state
                                    // manager updates observer.
                                    syncCoordinator.triggerSync()
                                }
                        )
                    }
                    is Action.MarkDeleted -> {
                        doMarkAsDeleted(action.notification).map { NotificationsRepositoryInterface.Emission.Update(it) }
                    }
                    is Action.MarkRead -> {
                        doMarkAsRead(action.notification).map { NotificationsRepositoryInterface.Emission.Update(it) }
                    }
                    is Action.NotificationsFetchedOrArrived -> {
                        mergeWithLocalStorage(action.notifications).flatMap { merged -> replaceLocalStorage(merged) }.map {
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
            val alreadyDeleted = onDisk.find { it.id == notification.id }?.isDeleted ?: false

            val modified = onDisk.map { onDiskNotification ->
                if (onDiskNotification.id == notification.id) {
                    onDiskNotification.copy(isDeleted = true)
                } else onDiskNotification
            }

            if (!alreadyDeleted) {
                eventQueue.trackEvent(
                    Event(
                        "Notification Marked Deleted",
                        hashMapOf(
                            Pair("notification", notification.asAttributeValue())
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
            val alreadyRead = onDisk.find { it.id == notification.id }?.isRead ?: false

            val modified = onDisk.map { onDiskNotification ->
                if (onDiskNotification.id == notification.id) {
                    onDiskNotification.copy(isRead = true)
                } else onDiskNotification
            }

            if (!alreadyRead) {
                eventQueue.trackEvent(
                    Event(
                        "Notification Marked Read",
                        hashMapOf(
                            Pair("notification", notification.asAttributeValue())
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

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "io.rover.rover.notification-storage"
        private const val STORE_KEY = "local-notifications-cache"

        private const val MAX_NOTIFICATIONS_LIMIT = 100
    }

    sealed class Action {
        /**
         * User has requested a refresh.
         */
        class Refresh : Action()

        /**
         * User has requested a refresh.
         */
        class MarkRead(val notification: Notification) : Action()

        /**
         * User has requested a mark to be delete.
         */
        class MarkDeleted(val notification: Notification) : Action()

        /**
         * Notifications arrived or fetched, and need to be merged in.
         */
        class NotificationsFetchedOrArrived(val notifications: List<Notification>) : Action()
    }
}

class NotificationsSyncResource(
    private val deviceIdentification: DeviceIdentificationInterface,
    private val notificationsRepository: NotificationsRepositoryInterface
): SyncResource<Notification> {
    override fun upsertObjects(nodes: List<Notification>) {
        notificationsRepository.mergeRetrievedNotifications(nodes)
    }

    override fun nextRequest(cursor: String?): SyncRequest {
        // Notifications don't use cursors.  The GraphQL API just gives us a fixed amount.
        log.v("Being asked for next sync request.")

        val values: HashMap<String, AttributeValue> = hashMapOf(
            Pair(SyncQuery.Argument.last.name, AttributeValue.Scalar.Integer(500)),
            Pair(SyncQuery.Argument.deviceIdentifier.name, AttributeValue.Scalar.String(deviceIdentification.installationIdentifier)),
            Pair(SyncQuery.Argument.orderBy.name, AttributeValue.Object(
                Pair("field", AttributeValue.Scalar.String("CREATED_AT")),
                Pair("direction", AttributeValue.Scalar.String("DESC"))
            ))
        )

        return SyncRequest(
            SyncQuery.notifications,
            values
        )
    }
}

class NotificationSyncDecoder(
    private val dateFormatting: DateFormattingInterface
): SyncDecoder<Notification> {
    override fun decode(json: JSONObject): GraphQLResponse<Notification> {
        return NotificationsSyncResponseData.decodeJson(
            json.getJSONObject("data"),
            dateFormatting
        ).notifications
    }
}

data class NotificationsSyncResponseData(
    val notifications: GraphQLResponse<Notification>
) {
    companion object
}

fun NotificationsSyncResponseData.Companion.decodeJson(json: JSONObject, dateFormatting: DateFormattingInterface): NotificationsSyncResponseData {
    return NotificationsSyncResponseData(
        notifications = GraphQLResponse.decodeNotificationsPageJson(
            json.getJSONObject("notifications"),
            dateFormatting
        )
    )
}

fun GraphQLResponse.Companion.decodeNotificationsPageJson(json: JSONObject, dateFormatting: DateFormattingInterface): GraphQLResponse<Notification> {
    return GraphQLResponse(
        nodes = json.getJSONArray("nodes").getObjectIterable().map { nodeJson ->
            Notification.decodeJson(nodeJson, dateFormatting)
        },
        pageInfo = null // paging not supported.
    )
}

val SyncQuery.Companion.notifications: SyncQuery
    get() = SyncQuery(
        "notifications",
        """
            nodes {
                ...notificationFields
            }
        """.trimIndent(),
        arguments = listOf(
            SyncQuery.Argument.last, SyncQuery.Argument.deviceIdentifier, SyncQuery.Argument.orderBy
        ),
        fragments = listOf("notificationFields")
    )

val SyncQuery.Argument.Companion.deviceIdentifier
    get() = SyncQuery.Argument("deviceIdentifier", "String!")


private val SyncQuery.Argument.Companion.orderBy
    get() = SyncQuery.Argument("orderBy", "NotificationOrder")
