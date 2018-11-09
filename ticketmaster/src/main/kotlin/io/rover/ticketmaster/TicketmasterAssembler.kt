package io.rover.ticketmaster

import io.rover.core.Rover
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.data.sync.SyncCoordinatorInterface
import io.rover.core.data.sync.SyncParticipant
import io.rover.core.events.UserInfoInterface
import io.rover.core.platform.LocalStorage

class TicketmasterAssembler: Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            TicketmasterAuthorizer::class.java
        ) { resolver ->
            resolver.resolveSingletonOrFail(TicketmasterManager::class.java)
        }

        container.register(
            Scope.Singleton,
            TicketmasterManager::class.java
        ) { resolver ->
            TicketmasterManager(
                resolver.resolveSingletonOrFail(UserInfoInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SyncParticipant::class.java,
            "ticketmaster"
        ) { resolver -> resolver.resolveSingletonOrFail(TicketmasterManager::class.java)}
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerParticipant(
            resolver.resolveSingletonOrFail(
                SyncParticipant::class.java,
                "ticketmaster"
            )
        )
    }
}

@Deprecated("Use .resolve(TicketmasterAuthorizer::class.java)")
val Rover.ticketmasterAuthorizer: TicketmasterAuthorizer
    get() = Rover.sharedInstance.resolveSingletonOrFail(TicketmasterAuthorizer::class.java)
