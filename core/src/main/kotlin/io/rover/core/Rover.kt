package io.rover.core

import android.content.Context
import io.rover.core.assets.AssetService
import io.rover.core.container.Assembler
import io.rover.core.container.ContainerResolver
import io.rover.core.container.InjectionContainer
import io.rover.core.data.http.AndroidHttpsUrlConnectionNetworkClient
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.logging.AndroidLogger
import io.rover.core.logging.GlobalStaticLogHolder
import io.rover.core.logging.LogBuffer
import io.rover.core.permissions.PermissionsNotifierInterface
import io.rover.core.routing.LinkOpenInterface
import java.net.HttpURLConnection

/**
 * Entry point for the Rover SDK.
 *
 * The Rover SDK consists of several discrete Plugins, which each offer a major vertical
 * (eg. Experiences, Location, and Events) of the Rover Platform.  It's up to you to select which
 * are appropriate to activate in your app.
 *
 * TODO: exhaustive usage information.
 *
 * Serves as a dependency injection container for the various components (Plugins) of the Rover SDK.
 */
class Rover(
    assemblers: List<Assembler>
): ContainerResolver by InjectionContainer(assemblers) {
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

    // These accessors for specific objects in DI exist for two reasons: access by top-level UI
    // objects, and access directly by developers' code. TODO re-evaluate that.

    val eventQueue: EventQueueServiceInterface
        get() = this.resolve(EventQueueServiceInterface::class.java) ?: throw missingDependencyError("EventQueueService", "Core")

    val permissionsNotifier: PermissionsNotifierInterface
        get() = this.resolve(PermissionsNotifierInterface::class.java) ?: throw missingDependencyError("PermissionsNotifier", neededAssembler = "Core")

    val linkOpen: LinkOpenInterface
        get() = this.resolve(LinkOpenInterface::class.java) ?: throw missingDependencyError("LinkOpen", "Experiences")

    val assetService: AssetService
        get() = this.resolve(AssetService::class.java) ?: throw missingDependencyError("AssetService", "Core")

    private fun missingDependencyError(name: String, neededAssembler: String): Throwable {
        throw RuntimeException("Dependency not registered: $name.  Did you include ${neededAssembler}Assembler() in the assembler list?")
    }

    companion object {
        private var sharedInstanceBackingField: Rover? = null

        // we have a global singleton of the Rover container.
        @JvmStatic
        val sharedInstance: Rover
            get() = sharedInstanceBackingField ?: throw RuntimeException("Rover shared instance accessed before calling initialize.\n\n" +
                "Did you remember to call Rover.initialize() in your Application.onCreate()?")

        @JvmStatic
        fun initialize(vararg assemblers: Assembler) {
            val rover = Rover(assemblers.asList())
            if(sharedInstanceBackingField != null) {
                throw RuntimeException("Rover already initialized.  This is most likely a bug.")
            }
            sharedInstanceBackingField = rover
        }

        /**
         * Be sure to always call this after [Rover.initialize] in your Application's onCreate()!
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
