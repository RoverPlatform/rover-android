package io.rover.campaigns.core

import android.content.Context
import io.rover.campaigns.core.BuildConfig
import io.rover.campaigns.core.container.Assembler
import io.rover.campaigns.core.container.ContainerResolver
import io.rover.campaigns.core.container.InjectionContainer
import io.rover.campaigns.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.campaigns.core.logging.AndroidLogger
import io.rover.campaigns.core.logging.GlobalStaticLogHolder
import io.rover.campaigns.core.logging.LogBuffer
import io.rover.campaigns.core.logging.log
import java.net.HttpURLConnection

/**
 * Entry point for the Rover SDK.
 *
 * The Rover SDK consists of several discrete modules, which each offer a major vertical
 * (eg. Experiences and Location) of the Rover Platform.  It's up to you to select which
 * are appropriate to activate in your app.
 *
 * TODO: exhaustive usage information.
 *
 * Serves as a dependency injection container for the various components (modules) of the Rover SDK.
 */
class RoverCampaigns(
    assemblers: List<Assembler>
) : ContainerResolver by InjectionContainer(assemblers) {
    init {
        // global, which we "inject" using static scope
        GlobalStaticLogHolder.globalLogEmitter =
            LogBuffer(
                // uses the resolver to discover when the EventQueueService is ready and can be used
                // to submit the logs.
                AndroidLogger()
            )

        initializeContainer()
    }

    companion object {
        private var sharedInstanceBackingField: RoverCampaigns? = null

        // we have a global singleton of the Rover container.
        @JvmStatic
        @Deprecated("Please use shared instead.")
        val sharedInstance: RoverCampaigns
            get() = sharedInstanceBackingField ?: throw RuntimeException("Rover shared instance accessed before calling initialize.\n\n" +
                "Did you remember to call RoverCampaigns.initialize() in your Application.onCreate()?")

        @JvmStatic
        val shared: RoverCampaigns?
            get() = sharedInstanceBackingField ?: log.w("Rover shared instance accessed before calling initialize.\n\n" +
                "Did you remember to call RoverCampaigns.initialize() in your Application.onCreate()?").let { null }

        @JvmStatic
        fun initialize(vararg assemblers: Assembler) {
            val rover = RoverCampaigns(assemblers.asList())
            if (sharedInstanceBackingField != null) {
                throw RuntimeException("Rover Campaigns already initialized.  This is most likely a bug.")
            }
            sharedInstanceBackingField = rover
            log.i("Started Rover Campaigns Android SDK v${BuildConfig.ROVER_CAMPAIGNS_VERSION}.")
        }

        /**
         * Be sure to always call this after [RoverCampaigns.initialize] in your Application's onCreate()!
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
    }
}
