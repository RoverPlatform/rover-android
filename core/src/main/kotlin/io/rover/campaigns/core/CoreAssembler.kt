@file:JvmName("Core")

package io.rover.campaigns.core

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.work.WorkManager
import io.rover.campaigns.core.assets.AndroidAssetService
import io.rover.campaigns.core.assets.AssetService
import io.rover.campaigns.core.assets.ImageDownloader
import io.rover.campaigns.core.container.Assembler
import io.rover.campaigns.core.container.Container
import io.rover.campaigns.core.container.Resolver
import io.rover.campaigns.core.container.Scope
import io.rover.campaigns.core.data.AuthenticationContext
import io.rover.campaigns.core.data.ServerKey
import io.rover.campaigns.core.data.graphql.GraphQlApiService
import io.rover.campaigns.core.data.graphql.GraphQlApiServiceInterface
import io.rover.campaigns.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.campaigns.core.data.http.NetworkClient
import io.rover.campaigns.core.data.sync.SyncByApplicationLifecycle
import io.rover.campaigns.core.data.sync.SyncClient
import io.rover.campaigns.core.data.sync.SyncClientInterface
import io.rover.campaigns.core.data.sync.SyncCoordinator
import io.rover.campaigns.core.data.sync.SyncCoordinatorInterface
import io.rover.campaigns.core.events.ContextProvider
import io.rover.campaigns.core.events.EventQueueService
import io.rover.campaigns.core.events.EventQueueServiceInterface
import io.rover.campaigns.core.events.UserInfo
import io.rover.campaigns.core.events.UserInfoInterface
import io.rover.campaigns.core.events.contextproviders.ApplicationContextProvider
import io.rover.campaigns.core.events.contextproviders.BluetoothContextProvider
import io.rover.campaigns.core.events.contextproviders.DarkModeContextProvider
import io.rover.campaigns.core.events.contextproviders.DeviceContextProvider
import io.rover.campaigns.core.events.contextproviders.DeviceIdentifierContextProvider
import io.rover.campaigns.core.events.contextproviders.LocaleContextProvider
import io.rover.campaigns.core.events.contextproviders.LocationServicesContextProvider
import io.rover.campaigns.core.events.contextproviders.ReachabilityContextProvider
import io.rover.campaigns.core.events.contextproviders.ScreenContextProvider
import io.rover.campaigns.core.events.contextproviders.SdkVersionContextProvider
import io.rover.campaigns.core.events.contextproviders.TelephonyContextProvider
import io.rover.campaigns.core.events.contextproviders.TimeZoneContextProvider
import io.rover.campaigns.core.events.contextproviders.UserInfoContextProvider
import io.rover.campaigns.core.permissions.PermissionsNotifier
import io.rover.campaigns.core.permissions.PermissionsNotifierInterface
import io.rover.campaigns.core.platform.DateFormatting
import io.rover.campaigns.core.platform.DateFormattingInterface
import io.rover.campaigns.core.platform.DeviceIdentification
import io.rover.campaigns.core.platform.DeviceIdentificationInterface
import io.rover.campaigns.core.platform.IoMultiplexingExecutor
import io.rover.campaigns.core.platform.LocalStorage
import io.rover.campaigns.core.platform.SharedPreferencesLocalStorage
import io.rover.campaigns.core.platform.whenNotNull
import io.rover.campaigns.core.routing.LinkOpenInterface
import io.rover.campaigns.core.routing.Router
import io.rover.campaigns.core.routing.RouterService
import io.rover.campaigns.core.routing.routes.OpenAppRoute
import io.rover.campaigns.core.routing.website.EmbeddedWebBrowserDisplay
import io.rover.campaigns.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.campaigns.core.streams.Scheduler
import io.rover.campaigns.core.streams.forAndroidMainThread
import io.rover.campaigns.core.streams.forExecutor
import io.rover.campaigns.core.tracking.ApplicationSessionEmitter
import io.rover.campaigns.core.tracking.SessionStore
import io.rover.campaigns.core.tracking.SessionStoreInterface
import io.rover.campaigns.core.tracking.SessionTracker
import io.rover.campaigns.core.tracking.SessionTrackerInterface
import io.rover.campaigns.core.ui.LinkOpen
import io.rover.campaigns.core.version.VersionTracker
import io.rover.campaigns.core.version.VersionTrackerInterface
import java.net.URL
import java.util.concurrent.Executor

/**
 * The core module of the Rover SDK.
 *
 * You must always pass an instance of Assembler to Rover.initialize().  The Core module provides
 * access to the Rover API and is required for all other Rover functionality.
 */

class CoreAssembler @JvmOverloads constructor(
    /**
     * Your Rover API account sdk token.  Can be found in the Rover Settings app under Account
     * Settings.
     */
    private val accountToken: String,
    private val application: Application,
    /**
     * Rover deep links are customized for each app in this way:
     *
     * rv-myapp://...
     *
     * You must set an appropriate scheme without spaces or special characters to be used in place
     * of `myapp` above.  It must match the value in your Rover Settings.
     *
     * You should also consider adding the handler to the manifest.  While this is not needed for
     * any Rover functionality to work, it is required for clickable deep links to work from
     * anywhere else.
     */
    private val urlSchemes: List<String>,

    /**
     * Rover universal links are customized for each in this way:
     *
     * myapp.rover.io
     *
     * You must set an appropriate domain without spaces or special characters to be used in place
     * of `myapp` above.  It must match the value in your Rover Settings.
     *
     * You should also consider adding the handler to the manifest.  While this is not needed for
     * any Rover functionality to work, it is required for clickable universal links to work from
     * anywhere else.
     */
    private val associatedDomains: List<String>,

    /**
     * An ARGB int color (typical on Android) that is used when Rover is asked to present a website
     * within the app (hosted within a an Android [Custom
     * Tab](https://developer.chrome.com/multidevice/android/customtabs)).  In many cases it is
     * appropriate to use the colour you use for your app toolbars.
     */
    @param:ColorInt
    private val chromeTabBackgroundColor: Int = Color.BLACK,

    /**
     * Specify an Intent that opens your app.  Defaults to the Activity you set as your launch
     * activity in your manifest.
     */
    private val openAppIntent: Intent? = null,

    /**
     * The location of the Rover API.  You should never need to change this.
     */
    private val endpoint: String = "https://api.rover.io/graphql",

    /**
     * By default the Rover SDK will schedule occasional background syncs (for instance, if you have
     * the Rover Location module installed, this will keep the monitored beacons and geofences up to
     * date).
     */
    private val scheduleBackgroundSync: Boolean = true
) : Assembler {

    override fun assemble(container: Container) {
        container.register(Scope.Singleton, Context::class.java) { _ ->
            application
        }

        container.register(Scope.Singleton, String::class.java, "accountToken") { _ -> accountToken }

        container.register(Scope.Singleton, Application::class.java) { _ ->
            application
        }


        if (openAppIntent != null || application.packageManager.getLaunchIntentForPackage(application.packageName) != null) {
            container.register(Scope.Singleton, Intent::class.java, "openApp") { _ ->
                openAppIntent ?: application.packageManager.getLaunchIntentForPackage(application.packageName) ?: Intent()
            }
        }

        container.register(Scope.Singleton, UrlSchemes::class.java) { _ ->
            urlSchemes.forEach { urlScheme ->
                when {
                    urlScheme.isBlank() -> throw RuntimeException("Deep link URL scheme must not be blank.")
                    !urlScheme.startsWith("rv-") -> throw RuntimeException("Rover Campaigns URI schemes must start with `rv-`.  See the documentation for Deep Links.")
                    urlScheme.contains(" ") -> throw RuntimeException("Deep link scheme slug must not contain spaces.")
                    // TODO: check for special characters.
                }
            }

            UrlSchemes(urlSchemes, associatedDomains)
        }

        container.register(Scope.Singleton, NetworkClient::class.java) { resolver ->
            AndroidHttpsUrlConnectionNetworkClient(
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                application.packageManager.getPackageInfo(application.packageName, 0)
            )
        }

        container.register(Scope.Singleton, Int::class.java, "chromeTabBackgroundColor") { _ ->
            chromeTabBackgroundColor
        }

        container.register(Scope.Singleton, DateFormattingInterface::class.java) { _ ->
            DateFormatting()
        }

        container.register(Scope.Singleton, Executor::class.java, "io") { _ ->
            IoMultiplexingExecutor.build("io")
        }

        container.register(Scope.Singleton, Scheduler::class.java, "main") { _ ->
            Scheduler.forAndroidMainThread()
        }

        container.register(Scope.Singleton, Scheduler::class.java, "io") { resolver ->
            Scheduler.forExecutor(
                resolver.resolveSingletonOrFail(Executor::class.java, "io")
            )
        }

        container.register(Scope.Singleton, LocalStorage::class.java) { _ ->
            SharedPreferencesLocalStorage(application)
        }

        container.register(Scope.Singleton, DeviceIdentificationInterface::class.java) { resolver ->
            DeviceIdentification(
                application,
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(Scope.Singleton, AuthenticationContext::class.java) { _ ->
            ServerKey(accountToken)
        }

        container.register(Scope.Singleton, GraphQlApiServiceInterface::class.java) { resolver ->
            GraphQlApiService(
                URL(endpoint),
                resolver.resolveSingletonOrFail(AuthenticationContext::class.java),
                resolver.resolveSingletonOrFail(NetworkClient::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java)
            )
        }

        container.register(Scope.Singleton, PermissionsNotifierInterface::class.java) { _ ->
            PermissionsNotifier(
                application
            )
        }

        container.register(Scope.Singleton, ImageDownloader::class.java) { resolver ->
            ImageDownloader(resolver.resolveSingletonOrFail(Executor::class.java, "io"))
        }

        container.register(Scope.Singleton, AssetService::class.java) { resolver ->
            AndroidAssetService(
                resolver.resolveSingletonOrFail(ImageDownloader::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main")
            )
        }

        container.register(Scope.Singleton, VersionTrackerInterface::class.java) { resolver ->
            VersionTracker(
                application,
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(Scope.Singleton, UserInfoInterface::class.java) { resolver ->
            UserInfo(
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "device") { _ ->
            DeviceContextProvider()
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "locale") { _ ->
            LocaleContextProvider(application.resources)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "darkMode") { _ ->
            DarkModeContextProvider(application)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "reachability") { _ ->
            ReachabilityContextProvider(application)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "screen") { _ ->
            ScreenContextProvider(application.resources)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "telephony") { _ ->
            TelephonyContextProvider(application)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "device") { _ ->
            DeviceContextProvider()
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "timeZone") { _ ->
            TimeZoneContextProvider()
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "locationAuthorization") { _ ->
            LocationServicesContextProvider(application)
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "attributes") { resolver ->
            UserInfoContextProvider(
                resolver.resolveSingletonOrFail(
                    UserInfoInterface::class.java
                )
            )
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "application") { _ ->
            ApplicationContextProvider(application)
        }

        container.register(
            Scope.Singleton,
            ContextProvider::class.java,
            "deviceIdentifier"
        ) { resolver ->
            DeviceIdentifierContextProvider(
                resolver.resolveSingletonOrFail(DeviceIdentificationInterface::class.java)
            )
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "sdkVersion") { _ ->
            SdkVersionContextProvider()
        }

        BluetoothAdapter.getDefaultAdapter().whenNotNull { bluetoothAdapter ->
            container.register(Scope.Singleton, BluetoothAdapter::class.java) { _ ->
                bluetoothAdapter
            }
        }

        container.register(Scope.Singleton, EventQueueServiceInterface::class.java) { resolver ->
            EventQueueService(
                resolver.resolveSingletonOrFail(GraphQlApiServiceInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                application,
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                20,
                30.0,
                50,
                1000
            )
        }

        container.register(Scope.Singleton, EmbeddedWebBrowserDisplayInterface::class.java) { _ ->
            EmbeddedWebBrowserDisplay(
                chromeTabBackgroundColor
            )
        }

        container.register(Scope.Singleton, SyncClientInterface::class.java) { resolver ->
            SyncClient(
                URL(endpoint),
                resolver.resolveSingletonOrFail(AuthenticationContext::class.java),
                resolver.resolveSingletonOrFail(DateFormattingInterface::class.java),
                resolver.resolveSingletonOrFail(NetworkClient::class.java)
            )
        }

        container.register(Scope.Singleton, SyncCoordinatorInterface::class.java) { resolver ->
            SyncCoordinator(
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "main"),
                resolver.resolveSingletonOrFail(SyncClientInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            Router::class.java
        ) { resolver ->
            RouterService(
                resolver.resolve(Intent::class.java, "openApp")
            )
        }

        container.register(
            Scope.Singleton,
            LinkOpenInterface::class.java
        ) { resolver ->
            LinkOpen(
                resolver.resolveSingletonOrFail(Router::class.java)
            )
        }

        container.register(Scope.Singleton, SessionStoreInterface::class.java) { resolver ->
            SessionStore(
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SessionTrackerInterface::class.java
        ) { resolver ->
            SessionTracker(
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(SessionStoreInterface::class.java),
                10
            )
        }

        container.register(
            Scope.Singleton,
            ApplicationSessionEmitter::class.java
        ) { resolver ->
            ApplicationSessionEmitter(
                ProcessLifecycleOwner.get().lifecycle,
                resolver.resolveSingletonOrFail(SessionTrackerInterface::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SyncByApplicationLifecycle::class.java
        ) { resolver ->
            SyncByApplicationLifecycle(
                resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java),
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                ProcessLifecycleOwner.get().lifecycle
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        val eventQueue = resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)

        listOf(
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "device"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "locale"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "darkMode"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "reachability"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "screen"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "telephony"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "timeZone"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "attributes"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "application"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "deviceIdentifier"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "sdkVersion"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "locationAuthorization")
        ).forEach { eventQueue.addContextProvider(it) }

        resolver.resolveSingletonOrFail(VersionTrackerInterface::class.java).trackAppVersion()

        resolver.resolveSingletonOrFail(ApplicationSessionEmitter::class.java).start()

        resolver.resolveSingletonOrFail(SyncByApplicationLifecycle::class.java).start()

        resolver.resolve(BluetoothAdapter::class.java).whenNotNull { bluetoothAdapter ->
            eventQueue.addContextProvider(BluetoothContextProvider(bluetoothAdapter))
        }

        resolver.resolveSingletonOrFail(Router::class.java).apply {
            registerRoute(
                OpenAppRoute(resolver.resolve(Intent::class.java, "openApp"))
            )
        }

        if (scheduleBackgroundSync) {
            resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java)
                .ensureBackgroundSyncScheduled()
        } else {
            // deschedule any prior rover sync jobs.
            WorkManager.getInstance(application).cancelAllWorkByTag("rover-sync")
        }
    }
}

data class UrlSchemes(
    val schemes: List<String>,
    val associatedDomains: List<String>
)

@Deprecated("Use .resolve(EventQueueServiceInterface::class.java)")
val RoverCampaigns.eventQueue: EventQueueServiceInterface
    get() = this.resolve(EventQueueServiceInterface::class.java)
        ?: throw missingDependencyError("EventQueueService")

@Deprecated("Use .resolve(PermissionsNotifierInterface::class.java)")
val RoverCampaigns.permissionsNotifier: PermissionsNotifierInterface
    get() = this.resolve(PermissionsNotifierInterface::class.java)
        ?: throw missingDependencyError("PermissionsNotifier")

@Deprecated("Use .resolve(LinkOpenInterface::class.java)")
val RoverCampaigns.linkOpen: LinkOpenInterface
    get() = this.resolve(LinkOpenInterface::class.java) ?: throw missingDependencyError("LinkOpen")

@Deprecated("Use .resolve(AssetService::class.java)")
val RoverCampaigns.assetService: AssetService
    get() = this.resolve(AssetService::class.java) ?: throw missingDependencyError("AssetService")

@Deprecated("Use .resolve(Router::class.java)")
val RoverCampaigns.router: Router
    get() = this.resolve(Router::class.java) ?: throw missingDependencyError("Router")

@Deprecated("Use .resolve(EmbeddedWebBrowserDisplayInterface::class.java)")
val RoverCampaigns.embeddedWebBrowserDisplay
    get() = this.resolve(EmbeddedWebBrowserDisplayInterface::class.java)
        ?: throw missingDependencyError("EmbeddedWebBrowserDisplayInterface")

@Deprecated("Use .resolve(DeviceIdentificationInterface::class.java)")
val RoverCampaigns.deviceIdentification
    get() = this.resolve(DeviceIdentificationInterface::class.java) ?: throw missingDependencyError(
        "DeviceIdentificationInterface"
    )

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include CoreAssembler() in the assembler list?")
}
