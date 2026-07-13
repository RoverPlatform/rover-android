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

@file:JvmName("Notifications")

package io.rover.sdk.notifications

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.rover.core.R
import io.rover.sdk.core.Rover
import io.rover.sdk.core.UrlSchemes
import io.rover.sdk.core.assets.AssetService
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.config.ConfigManager
import io.rover.sdk.core.data.sync.RealPagedSyncParticipant
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.data.sync.SyncDecoder
import io.rover.sdk.core.data.sync.SyncParticipant
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.PushTokenTransmissionChannel
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.contextproviders.FirebasePushTokenContextProvider
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.tracking.SessionTrackerInterface
import io.rover.sdk.core.ui.concerns.BindableView
import io.rover.sdk.notifications.domain.Notification
import io.rover.sdk.notifications.routing.routes.PresentNotificationCenterRoute
import io.rover.sdk.notifications.ui.InboxListViewModel
import io.rover.sdk.notifications.ui.NotificationItemView
import io.rover.sdk.notifications.ui.NotificationItemViewModel
import io.rover.sdk.notifications.ui.concerns.InboxListViewModelInterface
import io.rover.sdk.notifications.ui.concerns.NotificationItemViewModelInterface
import io.rover.sdk.notifications.ui.concerns.NotificationStoreInterface
import io.rover.sdk.notifications.ui.containers.InboxActivity
import io.rover.sdk.notifications.communicationhub.badge.RoverBadge
import io.rover.sdk.notifications.communicationhub.badge.RoverBadgeInterface
import io.rover.sdk.notifications.communicationhub.data.RoverEngageDatabase
import io.rover.sdk.notifications.communicationhub.data.network.EngageHttpClient
import io.rover.sdk.notifications.communicationhub.data.network.EngageApiService
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsRepository
import io.rover.sdk.notifications.communicationhub.conversations.RoomConversationsTransactionRunner
import io.rover.sdk.notifications.communicationhub.posts.PostsRepository
import io.rover.sdk.notifications.communicationhub.conversations.ConversationsSync
import io.rover.sdk.notifications.communicationhub.conversations.ParticipantsSync
import io.rover.sdk.notifications.communicationhub.posts.PostsSync
import io.rover.sdk.notifications.communicationhub.posts.SubscriptionsSync
import io.rover.sdk.notifications.communicationhub.AndroidDeliveredHubNotificationClearer
import io.rover.sdk.notifications.communicationhub.navigation.HubCoordinator
import io.rover.sdk.notifications.communicationhub.sync.HubSyncCoordinator
import io.rover.sdk.notifications.communicationhub.conversations.AndroidConversationPushNotificationPresenter
import io.rover.sdk.notifications.communicationhub.conversations.ConversationPushHandler
import io.rover.sdk.notifications.communicationhub.conversations.ConversationPushNotificationPresenter
import io.rover.sdk.notifications.communicationhub.posts.PostPushHandler
import io.rover.sdk.notifications.communicationhub.posts.ShowPostRoute
import io.rover.sdk.notifications.communicationhub.conversations.ShowConversationRoute
import io.rover.sdk.notifications.communicationhub.conversations.StandaloneConversationVisibilityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.Executor

class NotificationsAssembler @JvmOverloads constructor(
    private val applicationContext: Context,

    /**
     * A small icon is necessary for Android push notifications.  Pass a drawable res id here.
     * Android will display it in the Android status bar when a notification from your app is
     * waiting, as a monochromatic silhouette.
     *
     * Android design guidelines suggest that you use a multi-level drawable for your general
     * application icon, such that if you want to use that same icon for your `smallIconResId` you
     * can specify one of its levels (using `smallIconDrawableLevel`) that is most appropriate as a
     * single-colour silhouette that can be used as a notification status bar small icon.
     */
    @param:DrawableRes
    private val smallIconResId: Int,

    /**
     * The drawable level of [smallIconResId] that should be used for the icon silhouette used in
     * the notification drawer.
     */
    private val smallIconDrawableLevel: Int = 0,

    /**
     * Since Android O, all notifications need to have a "channel" selected, the list of which is
     * defined by the app the developer.  The channels are meant to discriminate between different
     * categories of push notification (eg. "account updates", "marketing messages", etc.) to allow
     * the user to configure which channels of message they want to see from your app right from the
     * Android notifications area.
     *
     * You should consider registering a set of channels when When configuring your campaigns you
     * should consider setting the channel ID.  If you do not, then the push notifications arriving
     * in your app through that campaign will instead be published to the Android notification area
     * with the default channel ID "Rover".
     *
     * If you give an unregistered channel, or leave it set as the default, "Rover", Rover will
     * attempt to register and configure it with the Android OS for you.  However, the channel
     * description and other metadata will be descriptive.
     */
    private val defaultChannelId: String = "rover",

    /**
     * The accent color of the small notification icon applied when the notification drawer is
     * expanded to display notifications.
     */
    private val iconColor: Int? = null,

    /**
     * Provide an [Intent] for opening your Inbox.  While you can refrain from
     * providing one, in that case the Rover SDK will use a very simple built-in version of the
     * Inbox which is probably not appropriate for the final version of your product.
     */
    private val inboxIntent: Intent = InboxActivity.makeIntent(applicationContext),

    /**
     * Rover will ask you to request a push token from Firebase, which is delivered back to you
     * asynchronously.  Then you should deliver the token back to Rover via a callback.
     *
     * Thus, your code should look something like the following:
     *
     * ```kotlin
     *  { tokenFutureCallback ->
     *    FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
     *      tokenFutureCallback(task.result?.token)
     *    }
     *  }
     * ```
     */
    private val requestPushToken: (tokenFutureCallback: (token: String?) -> Unit) -> Unit
) : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            NotificationStoreInterface::class.java
        ) { resolver ->
            NotificationStore(
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(Executor::class.java, "io"),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            NotificationsSyncResource::class.java
        ) { resolver ->
            NotificationsSyncResource(
                resolver.resolveSingletonOrFail(DeviceIdentificationInterface::class.java),
                resolver.resolveSingletonOrFail(NotificationStoreInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SyncDecoder::class.java,
            "notifications"
        ) { resolver ->
            NotificationSyncDecoder(resolver.resolveSingletonOrFail(DateFormattingInterface::class.java))
        }

        container.register(
            Scope.Singleton,
            SyncParticipant::class.java,
            "notifications"
        ) { resolver ->
            RealPagedSyncParticipant(
                resolver.resolveSingletonOrFail(NotificationsSyncResource::class.java),
                resolver.resolveSingletonOrFail(SyncDecoder::class.java, "notifications") as SyncDecoder<Notification>
            )
        }

        // adds an additional context provider to the Events system (which itself is in Core)
        // to capture the push token and ship it up via an Event.
        container.register(Scope.Singleton, ContextProvider::class.java, "pushToken") { resolver ->
            FirebasePushTokenContextProvider(
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        // and now re-register a factory to return that same object instance, but as the
        // PushTokenTransmissionChannel.
        container.register(Scope.Singleton, PushTokenTransmissionChannel::class.java) { resolver ->
            resolver.resolve(ContextProvider::class.java, "pushToken") as PushTokenTransmissionChannel
        }

        container.register(
            Scope.Transient, // can be a singleton because it is stateless and has no parameters.
            InboxListViewModelInterface::class.java
        ) { resolver, activityLifecycle: Lifecycle ->
            InboxListViewModel(
                resolver.resolveSingletonOrFail(NotificationStoreInterface::class.java),
                resolver.resolveSingletonOrFail(SessionTrackerInterface::class.java),
                activityLifecycle
            )
        }

        container.register(
            Scope.Transient,
            BindableView::class.java,
            "notificationItemView"
        ) { _, context: Context ->
            NotificationItemView(context)
        }

        container.register(
            Scope.Transient,
            View::class.java,
            "notificationItemSwipeToDeleteBackgroundView"
        ) { _, context: Context ->
            LayoutInflater.from(context).inflate(
                R.layout.notification_center_default_item_delete_swipe_reveal,
                null
            )
        }

        container.register(
            Scope.Transient,
            View::class.java,
            "notificationListEmptyArea"
        ) { _, context: Context ->
            TextView(context).apply {
                text = "" // Set a copy string to display here.
            }.apply {
                // center it in the display.
                gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            }
        }

        container.register(
            Scope.Transient,
            NotificationItemViewModelInterface::class.java
        ) { resolver, notification: Notification ->
            NotificationItemViewModel(
                notification,
                resolver.resolveSingletonOrFail(AssetService::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            NotificationOpenInterface::class.java
        ) { resolver ->
            NotificationOpen(
                applicationContext,
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(Router::class.java),
                resolver.resolve(Intent::class.java, "openApp"),
                resolver.resolveSingletonOrFail(EmbeddedWebBrowserDisplayInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            InfluenceTrackerServiceInterface::class.java
        ) { resolver ->
            InfluenceTrackerService(
                resolver.resolveSingletonOrFail(Application::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(NotificationOpenInterface::class.java),
                ProcessLifecycleOwner.get().lifecycle
            )
        }

        container.register(
            Scope.Singleton,
            PushReceiverInterface::class.java
        ) { resolver ->
            PushReceiver(
                resolver.resolveSingletonOrFail(PushTokenTransmissionChannel::class.java),
                resolver.resolveSingletonOrFail(NotificationDispatcher::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java),
                resolver.resolveSingletonOrFail(PostPushHandler::class.java),
                resolver.resolveSingletonOrFail(ConversationPushHandler::class.java)
            )
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "notification") { _ ->
            NotificationContextProvider(applicationContext)
        }

        container.register(
            Scope.Singleton,
            NotificationDispatcher::class.java
        ) { resolver ->
            NotificationDispatcher(
                applicationContext,
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                resolver.resolveSingletonOrFail(NotificationStoreInterface::class.java),
                resolver.resolveSingletonOrFail(NotificationOpenInterface::class.java),
                resolver.resolveSingletonOrFail(AssetService::class.java),
                resolver.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java),
                smallIconResId,
                smallIconDrawableLevel,
                defaultChannelId,
                iconColor
            )
        }
        
        // Engage API services
        container.register(
            Scope.Singleton,
            RoverEngageDatabase::class.java
        ) { _ ->
            RoverEngageDatabase.getDatabase(applicationContext)
        }
        
        container.register(
            Scope.Singleton,
            EngageHttpClient::class.java
        ) { resolver ->
            EngageHttpClient(
                applicationContext,
                resolver.resolve(String::class.java, "accountToken"),
                resolver.resolveSingletonOrFail(AuthenticationContextInterface::class.java),
                resolver.resolveSingletonOrFail(UserInfoInterface::class.java),
                resolver.resolveSingletonOrFail(DeviceIdentificationInterface::class.java)
            )
        }
        
        container.register(
            Scope.Singleton,
            EngageApiService::class.java
        ) { resolver ->
            EngageApiService.create(
                resolver.resolveSingletonOrFail(EngageHttpClient::class.java),
                resolver.resolve(String::class.java, "engageEndpoint") ?: "https://engage.rover.io/"
            )
        }
        
        container.register(
            Scope.Singleton,
            HubSyncCoordinator::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            HubSyncCoordinator(
                engageApiService = resolver.resolveSingletonOrFail(EngageApiService::class.java),
                postsDao = database.postsDao(),
                subscriptionsDao = database.subscriptionsDao(),
                conversationsDao = database.conversationsDao(),
                repliesDao = database.repliesDao(),
                participantsDao = database.participantsDao(),
                syncStateDao = database.syncStateDao(),
                transactionRunner = RoomConversationsTransactionRunner(database),
                hubCoordinator = resolver.resolveSingletonOrFail(HubCoordinator::class.java),
                deliveredHubNotificationClearer = AndroidDeliveredHubNotificationClearer(applicationContext),
            )
        }

        container.register(
            Scope.Singleton,
            PostsRepository::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            PostsRepository(
                database.postsDao(),
                database.subscriptionsDao(),
            )
        }

        container.register(
            Scope.Singleton,
            ConversationsRepository::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            ConversationsRepository(
                hubSyncCoordinator = resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java),
                conversationsDao = database.conversationsDao(),
                repliesDao = database.repliesDao(),
                participantsDao = database.participantsDao(),
                syncStateDao = database.syncStateDao(),
                transactionRunner = RoomConversationsTransactionRunner(database),
            )
        }

        container.register(
            Scope.Singleton,
            SubscriptionsSync::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            SubscriptionsSync(
                hubSyncCoordinator = resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java),
                subscriptionsDao = database.subscriptionsDao(),
                transactionRunner = RoomConversationsTransactionRunner(database),
            )
        }

        container.register(
            Scope.Singleton,
            PostsSync::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            PostsSync(
                hubSyncCoordinator = resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java),
                postsRepository = resolver.resolveSingletonOrFail(PostsRepository::class.java),
                syncStateDao = database.syncStateDao(),
                deviceIdentification = resolver.resolveSingletonOrFail(DeviceIdentificationInterface::class.java),
                transactionRunner = RoomConversationsTransactionRunner(database),
            )
        }

        container.register(
            Scope.Singleton,
            ConversationsSync::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            ConversationsSync(
                conversationsRepository = resolver.resolveSingletonOrFail(ConversationsRepository::class.java),
                hubSyncCoordinator = resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java),
                conversationsDao = database.conversationsDao(),
                participantsDao = database.participantsDao(),
                syncStateDao = database.syncStateDao(),
                transactionRunner = RoomConversationsTransactionRunner(database),
            )
        }

        container.register(
            Scope.Singleton,
            ParticipantsSync::class.java
        ) { resolver ->
            val database = resolver.resolveSingletonOrFail(RoverEngageDatabase::class.java)
            ParticipantsSync(
                hubSyncCoordinator = resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java),
                participantsDao = database.participantsDao(),
                transactionRunner = RoomConversationsTransactionRunner(database),
            )
        }
        
        container.register(
            Scope.Singleton,
            PostPushHandler::class.java
        ) { resolver ->
            PostPushHandler(
                resolver.resolveSingletonOrFail(PostsRepository::class.java),
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }

        container.register(
            Scope.Singleton,
            ConversationPushNotificationPresenter::class.java,
        ) { resolver ->
            AndroidConversationPushNotificationPresenter(
                applicationContext = applicationContext,
                smallIconResId = smallIconResId,
                smallIconDrawableLevel = smallIconDrawableLevel,
                defaultChannelId = defaultChannelId,
                iconColor = iconColor,
                hubCoordinator = resolver.resolveSingletonOrFail(HubCoordinator::class.java),
                standaloneConversationVisibilityTracker = resolver.resolveSingletonOrFail(StandaloneConversationVisibilityTracker::class.java),
            )
        }

        container.register(
            Scope.Singleton,
            ConversationPushHandler::class.java,
        ) { resolver ->
            ConversationPushHandler(
                conversationPushRepository = resolver.resolveSingletonOrFail(ConversationsRepository::class.java),
                notificationPresenter = resolver.resolveSingletonOrFail(ConversationPushNotificationPresenter::class.java),
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
        
        container.register(
            Scope.Singleton,
            RoverBadgeInterface::class.java
        ) { resolver ->
            RoverBadge(
                postsRepository = resolver.resolveSingletonOrFail(PostsRepository::class.java),
                conversationsRepository = resolver.resolveSingletonOrFail(ConversationsRepository::class.java),
            )
        }
        
        container.register(
            Scope.Singleton,
            HubCoordinator::class.java
        ) { _ ->
            HubCoordinator()
        }

        container.register(
            Scope.Singleton,
            StandaloneConversationVisibilityTracker::class.java
        ) { _ ->
            StandaloneConversationVisibilityTracker()
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        val eventQueue = resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
        // wire up the push context provider such that the current push token can always be
        // included with outgoing events.
        val pushTokenContextProvider = resolver.resolveSingletonOrFail(ContextProvider::class.java, "pushToken")
        eventQueue.addContextProvider(
            pushTokenContextProvider
        )

        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java).addContextProvider(
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "notification")
        )

        resolver.resolveSingletonOrFail(NotificationStoreInterface::class.java)

        resolver.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java).startListening()

        resolver.resolveSingletonOrFail(Router::class.java).apply {
            registerRoute(
                PresentNotificationCenterRoute(
                    resolver.resolveSingletonOrFail(UrlSchemes::class.java).schemes,
                    inboxIntent
                )
            )
            registerRoute(
                ShowPostRoute(
                    applicationContext,
                    resolver.resolveSingletonOrFail(UrlSchemes::class.java).schemes.toSet(),
                    resolver.resolveSingletonOrFail(HubCoordinator::class.java),
                    resolver.resolveSingletonOrFail(ConfigManager::class.java)
                )
            )
            registerRoute(
                ShowConversationRoute(
                    context = applicationContext,
                    urlSchemes = resolver.resolveSingletonOrFail(UrlSchemes::class.java).schemes.toSet(),
                    hubCoordinator = resolver.resolveSingletonOrFail(HubCoordinator::class.java),
                    configManager = resolver.resolveSingletonOrFail(ConfigManager::class.java),
                )
            )
        }

        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerParticipant(
            resolver.resolveSingletonOrFail(SyncParticipant::class.java, "notifications")
        )

        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerStandaloneParticipant(
            resolver.resolveSingletonOrFail(SubscriptionsSync::class.java)
        )

        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerStandaloneParticipant(
            resolver.resolveSingletonOrFail(PostsSync::class.java)
        )

        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerStandaloneParticipant(
            resolver.resolveSingletonOrFail(ConversationsSync::class.java)
        )

        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerStandaloneParticipant(
            resolver.resolveSingletonOrFail(ParticipantsSync::class.java)
        )

        resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java).registerResetObserver(
            resolver.resolveSingletonOrFail(ConversationsRepository::class.java)
        )

        resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java).registerResetCancellable(
            resolver.resolveSingletonOrFail(SubscriptionsSync::class.java)
        )
        resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java).registerResetCancellable(
            resolver.resolveSingletonOrFail(PostsSync::class.java)
        )
        resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java).registerResetCancellable(
            resolver.resolveSingletonOrFail(ConversationsSync::class.java)
        )
        resolver.resolveSingletonOrFail(HubSyncCoordinator::class.java).registerResetCancellable(
            resolver.resolveSingletonOrFail(ParticipantsSync::class.java)
        )

        requestPushToken { userProvidedToken ->
            resolver.resolveSingletonOrFail(PushReceiverInterface::class.java).onTokenRefresh(userProvidedToken)
        }
    }
}

val Rover.pushReceiver: PushReceiverInterface
    get() = this.resolve(PushReceiverInterface::class.java) ?: throw missingDependencyError("PushReceiverInterface")

val Rover.notificationOpen: NotificationOpenInterface
    get() = this.resolve(NotificationOpenInterface::class.java) ?: throw missingDependencyError("NotificationOpenInterface")

val Rover.influenceTracker: InfluenceTrackerServiceInterface
    get() = this.resolve(InfluenceTrackerServiceInterface::class.java) ?: throw missingDependencyError("InfluenceTrackerService")

val Rover.notificationStore: NotificationStoreInterface
    get() = this.resolve(NotificationStoreInterface::class.java) ?: throw missingDependencyError("NotificationsStoreInterface")

val Rover.roverBadge: RoverBadgeInterface
    get() = this.resolve(RoverBadgeInterface::class.java) ?: throw missingDependencyError("RoverBadgeInterface")

val Rover.hubCoordinator: HubCoordinator
    get() = this.resolve(HubCoordinator::class.java) ?: throw missingDependencyError("HubCoordinator")

internal val Rover.standaloneConversationVisibilityTracker: StandaloneConversationVisibilityTracker
    get() = this.resolve(StandaloneConversationVisibilityTracker::class.java)
        ?: throw missingDependencyError("StandaloneConversationVisibilityTracker")

internal val Rover.conversationPushNotificationPresenter: ConversationPushNotificationPresenter
    get() = this.resolve(ConversationPushNotificationPresenter::class.java)
        ?: throw missingDependencyError("ConversationPushNotificationPresenter")

internal val Rover.roverEngageDatabase: RoverEngageDatabase
    get() = this.resolve(RoverEngageDatabase::class.java) ?: throw missingDependencyError("CommunicationHubDatabase")

internal val Rover.postsRepository: PostsRepository
    get() = this.resolve(PostsRepository::class.java) ?: throw missingDependencyError("RoverEngageRepository")

internal val Rover.conversationsRepository: ConversationsRepository
    get() = this.resolve(ConversationsRepository::class.java) ?: throw missingDependencyError("ConversationsRepository")

internal val Rover.conversationsSync: ConversationsSync
    get() = this.resolve(ConversationsSync::class.java) ?: throw missingDependencyError("ConversationsSync")

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include NotificationsAssembler() in the assembler list?")
}
