package io.rover.sdk.advertising

import android.content.Context
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.streams.Scheduler

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
