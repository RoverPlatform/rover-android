@file:JvmName("Debug")

package io.rover.debug

import android.content.Context
import io.rover.core.Rover
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.platform.DeviceIdentificationInterface
import io.rover.core.routing.Router
import io.rover.debug.routes.DebugRoute

/**
 * The Debug module adds certain useful bits of debug functionality to the Rover SDK, namely a
 * new `isTestDevice` boolean to each event that is tracked through the EventQueue and a hidden
 * activity for managing its value.
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

@Deprecated("Use .resolve(DebugPreferences::class.java)")
val Rover.debugPreferences: DebugPreferences
    get() = this.resolve(DebugPreferences::class.java) ?: throw missingDependencyError("DebugPreferences")

private fun missingDependencyError(name: String): Throwable {
    throw RuntimeException("Dependency not registered: $name.  Did you include DebugAssembler() in the assembler list?")
}
