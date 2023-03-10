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

import io.rover.sdk.core.data.graphql.getObjectIterable
import io.rover.sdk.core.data.sync.GraphQLResponse
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.data.sync.SyncDecoder
import io.rover.sdk.core.data.sync.SyncQuery
import io.rover.sdk.core.data.sync.SyncRequest
import io.rover.sdk.core.data.sync.SyncResource
import io.rover.sdk.core.data.sync.last
import io.rover.sdk.core.events.EventQueueService
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.domain.Event
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.merge
import io.rover.sdk.core.platform.whenNotNull
import io.rover.sdk.core.streams.*
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.domain.events.asAttributeValue
import io.rover.sdk.notifications.graphql.decodeJson
import io.rover.sdk.notifications.graphql.encodeJson
import io.rover.sdk.notifications.ui.concerns.NotificationStoreInterface
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
class NotificationStore(
    private val dateFormatting: DateFormattingInterface,
    private val ioExecutor: Executor,
    private val mainThreadScheduler: Scheduler,
    private val eventQueue: EventQueueServiceInterface,
    private val syncCoordinator: SyncCoordinatorInterface,
    localStorage: LocalStorage
) : NotificationStoreInterface {
    override fun notifications(): Publisher<List<Notification>> = Publishers.concat(
        currentNotificationsOnDisk().map { existingNotifications ->
            existingNotifications
        },
        epic.filterForSubtype<NotificationStoreInterface.Emission.Update, NotificationStoreInterface.Emission>().map { update ->
            update.notifications
        }
    ).observeOn(mainThreadScheduler).filterForSubtype()

    override fun unreadCount(): Publisher<Int> = notifications().map { notifications ->
        notifications.count {
            it.isNotificationCenterEnabled && !it.isRead && !it.isDeleted
        }
    }.observeOn(mainThreadScheduler).shareAndReplay(1)

    override fun events(): Publisher<NotificationStoreInterface.Emission.Event> = epic.filterForSubtype()

    override fun refresh() {
        actions.onNext(Action.Refresh)
    }

    override fun markRead(notification: Notification) {
        actions.onNext(Action.MarkRead(notification))
    }

    override fun markAllAsRead() {
        actions.onNext(Action.MarkAllRead)
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
     * Add the existing notifications on disk together with any new ones.
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
        when (syncResult) {
            io.rover.sdk.core.data.sync.SyncCoordinatorInterface.Result.Succeeded -> Publishers.just(NotificationStoreInterface.Emission.Event.Refreshing(false))
            else -> {
                listOf(
                    NotificationStoreInterface.Emission.Event.Refreshing(false),
                    NotificationStoreInterface.Emission.Event.FetchFailure("Sync failed.")
                ).asPublisher()
            }
        }
    }

    private val epic: Publisher<NotificationStoreInterface.Emission> =
        Publishers.merge(
            actions.flatMap { action ->
                when (action) {
                    is Action.Refresh -> {
                        Publishers.concat(
                            Publishers.just(NotificationStoreInterface.Emission.Event.Refreshing(true))
                                .doOnComplete {
                                    log.v("Triggering Sync Coordinator refresh by request.")
                                    // this will result in an emission being received by the state
                                    // manager updates observer.
                                    syncCoordinator.triggerSync()
                                }
                        )
                    }
                    is Action.MarkDeleted -> {
                        doMarkAsDeleted(action.notification).map { NotificationStoreInterface.Emission.Update(it) }
                    }
                    is Action.MarkRead -> {
                        doMarkAsRead(action.notification).map { NotificationStoreInterface.Emission.Update(it) }
                    }
                    is Action.MarkAllRead -> {
                        doMarkAllRead().map { NotificationStoreInterface.Emission.Update(it) }
                    }
                    is Action.NotificationsFetchedOrArrived -> {
                        mergeWithLocalStorage(action.notifications).flatMap { merged -> replaceLocalStorage(merged) }.map {
                            NotificationStoreInterface.Emission.Update(it)
                        }
                    }
                }
            },
            stateStoreObserverChain
        ).observeOn(mainThreadScheduler)
            .shareHotAndReplay(0)

    private fun emitMarkedReadEvent(notification: Notification) {
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
                    io.rover.sdk.core.events.domain.Event(
                        "Notification Marked Deleted",
                        hashMapOf(
                            Pair("notification", notification.asAttributeValue())
                        )
                    ),
                    io.rover.sdk.core.events.EventQueueService.ROVER_NAMESPACE
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
                emitMarkedReadEvent(notification)
            }

            replaceLocalStorage(
                modified
            ).map { modified }
        }.subscribeOn(ioExecutor)
    }

    private fun doMarkAllRead(): Publisher<List<Notification>> {
        return currentNotificationsOnDisk().flatMap { onDisk ->
            val unread = onDisk.filter { !it.isRead }
            val modified = onDisk.map { onDiskNotification ->
                onDiskNotification.copy(isRead = true)
            }

            replaceLocalStorage(modified).map { modified }.doOnComplete {
                // side-effect: emit all marked-read events.
                unread.forEach { emitMarkedReadEvent(it) }
            }
        }
    }

    private fun Collection<Notification>.orderNotifications(): List<Notification> {
        return this
            .sortedByDescending { it.deliveredAt }
    }

    companion object {
        private const val STORAGE_CONTEXT_IDENTIFIER = "notification-storage"
        private const val STORE_KEY = "local-notifications-cache"

        private const val MAX_NOTIFICATIONS_LIMIT = 100
    }

    sealed class Action {
        /**
         * User has requested a refresh.
         */
        object Refresh : Action()

        /**
         * User has requested that a notification be marked as read.
         */
        class MarkRead(val notification: Notification) : Action()

        /**
         * User has requested a notification to be marked as deleted.
         */
        class MarkDeleted(val notification: Notification) : Action()

        /**
         * User has requested that all unread notifications be marked as read.
         */
        object MarkAllRead : Action()

        /**
         * Notifications arrived or fetched, and need to be merged in.
         */
        class NotificationsFetchedOrArrived(val notifications: List<Notification>) : Action()
    }
}

class NotificationsSyncResource(
    private val deviceIdentification: DeviceIdentificationInterface,
    private val notificationsRepository: NotificationStoreInterface
) : SyncResource<Notification> {
    override fun upsertObjects(nodes: List<Notification>) {
        notificationsRepository.mergeRetrievedNotifications(nodes)
    }

    override fun nextRequest(cursor: String?): SyncRequest {
        // Notifications don't use cursors.  The GraphQL API just gives us a fixed amount.
        log.v("Being asked for next sync request.")

        val values: HashMap<String, Any> = hashMapOf(
            Pair(SyncQuery.Argument.last.name, 500),
            Pair(SyncQuery.Argument.deviceIdentifier.name, deviceIdentification.installationIdentifier),
            Pair(
                SyncQuery.Argument.orderBy.name,
                hashMapOf(
                    Pair("field", "CREATED_AT"),
                    Pair("direction", "DESC")
                )
            )
        )

        return SyncRequest(
            SyncQuery.notifications,
            values
        )
    }
}

class NotificationSyncDecoder(
    private val dateFormatting: DateFormattingInterface
) : SyncDecoder<Notification> {
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
            SyncQuery.Argument.last,
            SyncQuery.Argument.deviceIdentifier,
            SyncQuery.Argument.orderBy
        ),
        fragments = listOf("notificationFields")
    )

val SyncQuery.Argument.Companion.deviceIdentifier
    get() = SyncQuery.Argument("deviceIdentifier", "String!")

private val SyncQuery.Argument.Companion.orderBy
    get() = SyncQuery.Argument("orderBy", "NotificationOrder")
