package io.rover.debug

import android.content.Context
import io.rover.debug.routes.DebugRoute
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.routing.Router
import io.rover.core.platform.DeviceIdentificationInterface

/**
 * The Debug module adds certain useful bits of debug functionality to the Rover SDK.
 *
 * Note that it may safely be used in production as well as dev builds of your app, but it is
 * optional should you want to be extra sure that debug functionality cannot be inadvertently
 * exposed.
 */
class DebugAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            DebugPreferences::class.java
        ) { resolver ->
            DebugPreferences(
                resolver.resolveSingletonOrFail(Context::class.java),
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java),
                resolver.resolveSingletonOrFail(DeviceIdentificationInterface::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(Router::class.java).apply {
            registerRoute(
                DebugRoute(
                    resolver.resolveSingletonOrFail(Context::class.java)
                )
            )
        }

        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            .addContextProvider(
                TestDeviceContextProvider(
                    resolver.resolveSingletonOrFail(DebugPreferences::class.java)
                )
            )
    }
}
