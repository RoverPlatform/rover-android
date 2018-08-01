package io.rover.notifications

import android.app.Application
import android.content.Context
import android.content.Intent
import android.support.annotation.DrawableRes
import io.rover.core.UrlSchemes
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
import io.rover.notifications.ui.containers.NotificationCenterActivity
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
     * Provide an [Intent] for opening your Notification Center.  While you can refrain from
     * providing one, in that case the Rover SDK will use a very simple built-in version of the
     * Notification Center which is probably not appropriate for the final version of your product.
     */
    private val notificationCenterIntent: Intent = NotificationCenterActivity.makeIntent(applicationContext),

    /**
     * While normally your `FirebaseInstanceIdService` class will be responsible for being
     * informed of push token changes, from time to time (particularly on app upgrades or when
     * Rover 2.0 is first integrated in your app) Rover may need to force a reset of your Firebase
     * push token.   However, you have to bridge the gap between Rover and the Firebase library
     * by providing a small bit of boilerplate.
     *
     * Please pass a block with the following contents:
     *
     * ```kotlin
     * FirebaseInstanceId.getInstance().deleteInstanceId()
     * FirebaseInstanceId.getInstance().token
     * ```
     *
     * Note: his closure will be called on a background worker thread.
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
                    resolver.resolveSingletonOrFail(UrlSchemes::class.java).schemes,
                    notificationCenterIntent
                )
            )
        }
    }
}
