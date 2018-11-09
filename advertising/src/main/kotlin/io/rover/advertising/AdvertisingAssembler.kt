package io.rover.advertising

import android.content.Context
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.events.ContextProvider
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.platform.LocalStorage
import io.rover.core.streams.Scheduler

/**
 * Add this module to your project to include the Google Advertising Identifier in your device
 * context sent with events.
 */
class AdvertisingAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            ContextProvider::class.java,
            "advertising"
        ) { resolver ->
            AdvertisingIdContentProvider(
                resolver.resolveSingletonOrFail(Context::class.java),
                resolver.resolveSingletonOrFail(Scheduler::class.java, "io"),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java).addContextProvider(
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "advertising")
        )
    }
}
