package io.rover.core

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.support.annotation.ColorInt
import io.rover.core.assets.AndroidAssetService
import io.rover.core.assets.ImageDownloader
import io.rover.core.data.graphql.GraphQlApiService
import io.rover.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.core.data.http.NetworkClient
import io.rover.core.events.EventEmitter
import io.rover.core.logging.log
import io.rover.core.platform.DateFormatting
import io.rover.core.platform.IoMultiplexingExecutor
import io.rover.core.platform.LocalStorage
import io.rover.core.streams.Scheduler
import io.rover.core.streams.forAndroidMainThread
import io.rover.core.streams.forExecutor
import io.rover.core.tracking.SessionStore
import io.rover.core.tracking.SessionTracker
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor


/**
 * Entry point for the Rover SDK.
 *
 * The Rover SDK consists of several discrete modules, which each offer a major vertical
 * (eg. Experiences and Location) of the Rover Platform.  It's up to you to select which
 * are appropriate to activate in your app.
 *
 * Serves as a dependency injection container (backplane) for the various components of the Rover
 * SDK.
 */
open class Rover(
    /**
     * When initializing Rover you must give it a reference
     */
    open val application: Application,

    /**
     * Set your Rover Account Token (API Key) here.
     */
    open var accountToken: String? = null,

    /**
     * Set the background colour for the Custom Chrome tabs that are used for presenting web content
     * in a web browser.
     */
    open val chromeTabBackgroundColor: Int,

    open val endpoint: String = "https://api.rover.io/graphql",

    open val dateFormatting: DateFormatting = DateFormatting(),

    open val mainScheduler: Scheduler = Scheduler.forAndroidMainThread(),

    open val ioExecutor: Executor = IoMultiplexingExecutor.build("io"),

    open val ioScheduler: Scheduler = Scheduler.forExecutor(
        ioExecutor
    ),

    open val imageDownloader: ImageDownloader = ImageDownloader(ioExecutor),

    open val assetService: AndroidAssetService = AndroidAssetService(imageDownloader, ioScheduler, mainScheduler),

    open val networkClient: NetworkClient = AndroidHttpsUrlConnectionNetworkClient(ioScheduler),

    open val webBrowserDisplay: EmbeddedWebBrowserDisplay = EmbeddedWebBrowserDisplay(chromeTabBackgroundColor),

    open val localStorage: LocalStorage = LocalStorage(application),

    open val sessionStore: SessionStore = SessionStore(localStorage, dateFormatting),

    open val eventEmitter: EventEmitter = EventEmitter(),

    /**
     * Not for use by typical applications: present so OAuth/SSO with apps that log into the Rover web apps can use the SDK.  You can safely ignore this.
     */
    open var bearerToken: String? = null,

    open val apiService: GraphQlApiService = GraphQlApiService(URL(endpoint), accountToken, bearerToken, networkClient),

    open val sessionTracker: SessionTracker = SessionTracker(eventEmitter, sessionStore, 60)
) {
    companion object {
        /**
         * Be sure to always call this after [Rover.dsfasdfasdfdasfda] in your Application's onCreate()!
         *
         * Rover internally uses the standard HTTP client included with Android, but to work
         * effectively it needs HTTP caching enabled.  Unfortunately, this can only be done at the
         * global level, so we ask that you call this method -- [installSaneGlobalHttpCache] -- at
         * application start time (unless you have already added your own cache to Android's
         * [HttpURLConnection].
         */
        @JvmStatic
        fun installSaneGlobalHttpCache(applicationContext: Context) {
            AndroidHttpsUrlConnectionNetworkClient.installSaneGlobalHttpCache(applicationContext)
        }

        fun initialize(application: Application, accountToken: String, @ColorInt chromeTabColor: Int = Color.BLACK) {
            shared = Rover(application = application, accountToken = accountToken, chromeTabBackgroundColor = chromeTabColor)
        }

        /**
         * Instantiate and set Rover here.  Use one of the [initialize] method to do so.
         */
        @JvmStatic
        var shared: Rover? = null

        // START HERE AND DO A COMMIT AND THEN INTERFACES FLATTEN/CLEANUP
        
    }

    init {
        log.i("Started Rover Android SDK v${BuildConfig.VERSION_NAME}.")
    }
}

