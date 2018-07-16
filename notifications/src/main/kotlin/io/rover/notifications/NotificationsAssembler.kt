package io.rover.notifications

import android.app.Application
import android.content.Context
import android.content.Intent
import android.support.annotation.DrawableRes
import io.rover.core.assets.AssetService
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.data.state.StateManagerServiceInterface
import io.rover.core.events.ContextProvider
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.PushTokenTransmissionChannel
import io.rover.core.events.contextproviders.FirebasePushTokenContextProvider
import io.rover.core.platform.DateFormattingInterface
import io.rover.core.platform.LocalStorage
import io.rover.core.routing.Router
import io.rover.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.core.streams.Scheduler
import io.rover.core.tracking.SessionTrackerInterface
import io.rover.notifications.routing.routes.PresentNotificationCenterRoute
import io.rover.notifications.ui.NotificationCenterListViewModel
import io.rover.notifications.ui.concerns.NotificationCenterListViewModelInterface
import io.rover.notifications.ui.concerns.NotificationsRepositoryInterface
import java.util.concurrent.Executor

class NotificationsAssembler @JvmOverloads constructor(
    private val applicationContext: Context,

    /**
     * A small icon is necessary for Android push notifications.  Pass a resid.
     *
     * Android design guidelines suggest that you use a multi-level drawable for your application
     * icon, such that you can specify one of its levels that is most appropriate as a single-colour
     * silhouette that can be used in the Android notification drawer.
     */
    @param:DrawableRes
    private val smallIconResId: Int,

    /**
     * The drawable level of [smallIconResId] that should be used for the icon silhouette used in
     * the notification drawer.
     */
    private val smallIconDrawableLevel: Int = 0,

    private val defaultChannelId: String = "rover",


    // TODO: default to a builtin intent a hosted notification center, possibly.
    /**
     * Provide an intent for opening your Notification Center, if you have created one.
     */
    private val notificationCenterIntent: Intent? = null,

    /**
     * While normally your `FirebaseInstanceIdService` class will be responsible for being
     * informed of push token changes, from time to time (particularly on app upgrades or when
     * Rover 2.0 is first integrated in your app) Rover may need to force a reset of your Firebase
     * push token.
     *
     * This closure will be called on a background worker thread.  Please pass a block with
     * the following contents:
     *
     * ```kotlin
     * FirebaseInstanceId.getInstance().deleteInstanceId()
     * FirebaseInstanceId.getInstance().token
     * ```
     */
    private val resetPushToken: () -> Unit
) : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            NotificationsRepositoryInterface::class.java
        ) { resolver ->
            NotificationsRepository(
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(Executor::class.java, "io"),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(StateManagerServiceInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        // adds an additional context provider to the Events system (which itself is in Core)
        // to capture the push token and ship it up via an Event.
        container.register(Scope.Singleton, ContextProvider::class.java, "pushToken") { resolver ->
            FirebasePushTokenContextProvider(
                resolver.resolveSingletonOrFail(LocalStorage::class.java),
                resetPushToken
            )
        }

        // and now re-register a factory to return that same object instance, but as the
        // PushTokenTransmissionChannel.
        container.register(Scope.Singleton, PushTokenTransmissionChannel::class.java) { resolver ->
            resolver.resolve(ContextProvider::class.java, "pushToken") as PushTokenTransmissionChannel
        }

        container.register(
            Scope.Transient, // can be a singleton because it is stateless and has no parameters.
            NotificationCenterListViewModelInterface::class.java
        ) { resolver ->
            NotificationCenterListViewModel(
                resolver.resolveSingletonOrFail(NotificationsRepositoryInterface::class.java),
                resolver.resolveSingletonOrFail(SessionTrackerInterface::class.java)
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
                resolver.resolveSingletonOrFail(Intent::class.java, "openApp"),
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
                resolver.resolveSingletonOrFail(NotificationOpenInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            PushReceiverInterface::class.java
        ) { resolver ->
            PushReceiver(
                resolver.resolveSingletonOrFail(PushTokenTransmissionChannel::class.java),
                resolver.resolveSingletonOrFail(NotificationDispatcher::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java)
            )
        }

        container.register(Scope.Singleton, ContextProvider::class.java,"notification") { _ ->
            NotificationContextProvider(applicationContext)
        }

        container.register(
            Scope.Singleton,
            NotificationDispatcher::class.java
        ) { resolver ->
            NotificationDispatcher(
                applicationContext,
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                resolver.resolveSingletonOrFail(NotificationsRepositoryInterface::class.java),
                resolver.resolveSingletonOrFail(NotificationOpenInterface::class.java),
                resolver.resolveSingletonOrFail(AssetService::class.java),
                resolver.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java),
                smallIconResId,
                smallIconDrawableLevel,
                defaultChannelId
            )
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

        resolver.resolveSingletonOrFail(NotificationsRepositoryInterface::class.java)

        resolver.resolveSingletonOrFail(InfluenceTrackerServiceInterface::class.java).startListening()

        resolver.resolveSingletonOrFail(Router::class.java).apply {
            registerRoute(
                PresentNotificationCenterRoute(
                    resolver.resolveSingletonOrFail(String::class.java, "deepLinkScheme"),
                    notificationCenterIntent
                )
            )
        }
    }
}
