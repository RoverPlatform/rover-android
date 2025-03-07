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

@file:JvmName("Core")

package io.rover.sdk.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import io.rover.sdk.core.assets.AndroidAssetService
import io.rover.sdk.core.assets.AssetService
import io.rover.sdk.core.assets.ImageDownloader
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.data.AuthenticationContextInterface
import io.rover.sdk.core.data.AuthenticationContext
import io.rover.sdk.core.data.graphql.GraphQlApiService
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.data.http.NetworkClient
import io.rover.sdk.core.data.http.OkHttpNetworkClient
import io.rover.sdk.core.data.sync.SyncByApplicationLifecycle
import io.rover.sdk.core.data.sync.SyncClient
import io.rover.sdk.core.data.sync.SyncClientInterface
import io.rover.sdk.core.data.sync.SyncCoordinator
import io.rover.sdk.core.data.sync.SyncCoordinatorInterface
import io.rover.sdk.core.events.AppLastSeenInterface
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueService
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.events.UserInfo
import io.rover.sdk.core.events.UserInfoInterface
import io.rover.sdk.core.events.contextproviders.ApplicationContextProvider
import io.rover.sdk.core.events.contextproviders.ConversionsContextProvider
import io.rover.sdk.core.events.contextproviders.DarkModeContextProvider
import io.rover.sdk.core.events.contextproviders.DeviceContextProvider
import io.rover.sdk.core.events.contextproviders.DeviceIdentifierContextProvider
import io.rover.sdk.core.events.contextproviders.LastSeenContextProvider
import io.rover.sdk.core.events.contextproviders.LocaleContextProvider
import io.rover.sdk.core.events.contextproviders.LocationServicesContextProvider
import io.rover.sdk.core.events.contextproviders.ReachabilityContextProvider
import io.rover.sdk.core.events.contextproviders.ScreenContextProvider
import io.rover.sdk.core.events.contextproviders.SdkVersionContextProvider
import io.rover.sdk.core.events.contextproviders.TelephonyContextProvider
import io.rover.sdk.core.events.contextproviders.TimeZoneContextProvider
import io.rover.sdk.core.events.contextproviders.UserInfoContextProvider
import io.rover.sdk.core.permissions.PermissionsNotifier
import io.rover.sdk.core.permissions.PermissionsNotifierInterface
import io.rover.sdk.core.platform.DateFormatting
import io.rover.sdk.core.platform.DateFormattingInterface
import io.rover.sdk.core.platform.DeviceIdentification
import io.rover.sdk.core.platform.DeviceIdentificationInterface
import io.rover.sdk.core.platform.IoMultiplexingExecutor
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.platform.SharedPreferencesLocalStorage
import io.rover.sdk.core.privacy.PrivacyService
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.core.routing.Router
import io.rover.sdk.core.routing.RouterService
import io.rover.sdk.core.routing.routes.OpenAppRoute
import io.rover.sdk.core.routing.website.EmbeddedWebBrowserDisplay
import io.rover.sdk.core.routing.website.EmbeddedWebBrowserDisplayInterface
import io.rover.sdk.core.streams.Scheduler
import io.rover.sdk.core.streams.forAndroidMainThread
import io.rover.sdk.core.streams.forExecutor
import io.rover.sdk.core.tracking.ApplicationSessionEmitter
import io.rover.sdk.core.tracking.ConversionsManager
import io.rover.sdk.core.tracking.ConversionsTrackerService
import io.rover.sdk.core.tracking.SessionStore
import io.rover.sdk.core.tracking.SessionStoreInterface
import io.rover.sdk.core.tracking.SessionTracker
import io.rover.sdk.core.tracking.SessionTrackerInterface
import io.rover.sdk.core.ui.LinkOpen
import io.rover.sdk.core.version.VersionTracker
import io.rover.sdk.core.version.VersionTrackerInterface
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

        container.register(Scope.Singleton, PrivacyService::class.java) { resolver ->
            PrivacyService(
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
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
                    !urlScheme.startsWith("rv-") -> throw RuntimeException("Rover URI schemes must start with `rv-`.  See the documentation for Deep Links.")
                    urlScheme.contains(" ") -> throw RuntimeException("Deep link scheme slug must not contain spaces.")
                    // TODO: check for special characters.
                }
            }

            UrlSchemes(urlSchemes.map { it.lowercase() }, associatedDomains.map { it.lowercase() })
        }

        container.register(Scope.Singleton, NetworkClient::class.java) { resolver ->
            OkHttpNetworkClient(application.packageManager.getPackageInfo(application.packageName, 0))
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

        container.register(Scope.Singleton, AuthenticationContextInterface::class.java) { resolver ->
            AuthenticationContext(accountToken, resolver.resolveSingletonOrFail(LocalStorage::class.java))
        }

        container.register(Scope.Singleton, GraphQlApiServiceInterface::class.java) { resolver ->
            GraphQlApiService(
                URL(endpoint),
                resolver.resolveSingletonOrFail(AuthenticationContextInterface::class.java),
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
            ImageDownloader(resolver.resolveSingletonOrFail(NetworkClient::class.java))
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

        container.register(Scope.Singleton, ContextProvider::class.java, "telephony") { resolver ->
            TelephonyContextProvider(application, resolver.resolveSingletonOrFail(PrivacyService::class.java))
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "device") { _ ->
            DeviceContextProvider()
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "timeZone") { _ ->
            TimeZoneContextProvider()
        }

        container.register(Scope.Singleton, ContextProvider::class.java, "locationAuthorization") { resolver ->
            LocationServicesContextProvider(
                application,
                resolver.resolveSingletonOrFail(PrivacyService::class.java)
            )
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

        container.register(Scope.Singleton, ContextProvider::class.java, "lastSeen") { resolver ->
            LastSeenContextProvider(
                    resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(Scope.Singleton, AppLastSeenInterface::class.java) { resolver ->
            resolver.resolve(ContextProvider::class.java, "lastSeen") as AppLastSeenInterface
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
                resolver.resolveSingletonOrFail(AuthenticationContextInterface::class.java),
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
            RouterService()
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

        container.register(
                Scope.Singleton,
                ConversionsManager::class.java
        ) { resolver ->
            ConversionsManager(
                    resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
                Scope.Singleton,
                ConversionsTrackerService::class.java
        ) { resolver ->
            ConversionsTrackerService(
                    resolver.resolveSingletonOrFail(ConversionsManager::class.java)
            )
        }

        container.register(
                Scope.Singleton,
                ContextProvider::class.java,
                "conversions"
        ) { resolver ->
            ConversionsContextProvider(
                    resolver.resolveSingletonOrFail(ConversionsManager::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        val eventQueue = resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)

        listOf(
            resolver.resolveSingletonOrFail(PrivacyService::class.java),
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
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "locationAuthorization"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "conversions"),
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "lastSeen"),
        ).forEach { eventQueue.addContextProvider(it) }

        resolver.resolveSingletonOrFail(VersionTrackerInterface::class.java).trackAppVersion()

        resolver.resolveSingletonOrFail(ApplicationSessionEmitter::class.java).start()

        resolver.resolveSingletonOrFail(SyncByApplicationLifecycle::class.java).start()

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

        resolver.resolveSingletonOrFail(ConversionsManager::class.java).apply {
            this.migrateLegacyTags()
        }

        resolver.resolveSingletonOrFail(PrivacyService::class.java).refreshAllListeners()
    }
}

data class UrlSchemes(
    val schemes: List<String>,
    val associatedDomains: List<String>
)

val Rover.eventQueue: EventQueueServiceInterface
    get() = this.resolve(EventQueueServiceInterface::class.java)
        ?: throw missingDependencyError("EventQueueService")

val Rover.permissionsNotifier: PermissionsNotifierInterface
    get() = this.resolve(PermissionsNotifierInterface::class.java)
        ?: throw missingDependencyError("PermissionsNotifier")

val Rover.linkOpen: LinkOpenInterface
    get() = this.resolve(LinkOpenInterface::class.java) ?: throw missingDependencyError("LinkOpen")

val Rover.assetService: AssetService
    get() = this.resolve(AssetService::class.java) ?: throw missingDependencyError("AssetService")

val Rover.router: Router
    get() = this.resolve(Router::class.java) ?: throw missingDependencyError("Router")

val Rover.embeddedWebBrowserDisplay
    get() = this.resolve(EmbeddedWebBrowserDisplayInterface::class.java)
        ?: throw missingDependencyError("EmbeddedWebBrowserDisplayInterface")

val Rover.deviceIdentification
    get() = this.resolve(DeviceIdentificationInterface::class.java) ?: throw missingDependencyError(
        "DeviceIdentificationInterface"
    )

val Rover.authenticationContext: AuthenticationContextInterface
    get() = this.resolve(AuthenticationContextInterface::class.java) ?: throw missingDependencyError("AuthenticationContext")

val Rover.userInfoManager: UserInfoInterface
    get() = this.resolve(UserInfoInterface::class.java) ?: throw missingDependencyError("UserInfoInterface")

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include CoreAssembler() in the assembler list?")
}

val Rover.privacyService: PrivacyService
    get() = this.resolve(PrivacyService::class.java) ?: throw missingDependencyError("PrivacyService")

var Rover.trackingMode: PrivacyService.TrackingMode
    get() = privacyService.trackingMode
    set(value) {
        privacyService.trackingMode = value
    }

/**
 * Set a JWT token for the signed-in user, signed (RS256 or better).
 *
 * This securely attests to the user's identity to enable additional personalization features.
 *
 * Call this method when your user signs in with your account system, and whenever you do your
 * token-refresh cycle.
 */
fun Rover.setSdkAuthorizationIdToken(token: String) {
    authenticationContext.setSdkAuthenticationIdToken(token)
}

/**
 * Clear the SDK authorization token.
 */
fun Rover.clearSdkAuthorizationIdToken() {
    authenticationContext.clearSdkAuthenticationIdToken()
}

/**
 * Register a callback to be called when the Rover SDK needs needs a refreshed SDK authorization
 * token.  When you have obtained a new token, set it as usual with [setSdkAuthorizationIdToken].
 *
 * If the token is needed for an interactive user operation (such as fetching an api.rover.io data
 * source), the SDK will wait for 10 seconds before timing out that operation.
 */
fun Rover.registerSdkAuthorizationIdTokenRefreshCallback(callback: () -> Unit) {
    this.authenticationContext.sdkAuthenticationIdTokenRefreshCallback = callback
}
