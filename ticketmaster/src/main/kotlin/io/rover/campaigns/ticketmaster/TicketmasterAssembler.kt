package io.rover.campaigns.ticketmaster

import android.app.Application
import android.content.IntentFilter
// ANDREW START HERE
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.rover.campaigns.core.RoverCampaigns
import io.rover.campaigns.core.container.Assembler
import io.rover.campaigns.core.container.Container
import io.rover.campaigns.core.container.Resolver
import io.rover.campaigns.core.container.Scope
import io.rover.campaigns.core.data.sync.SyncCoordinatorInterface
import io.rover.campaigns.core.data.sync.SyncParticipant
import io.rover.campaigns.core.events.UserInfoInterface
import io.rover.campaigns.core.platform.LocalStorage

class TicketmasterAssembler : Assembler {
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
                resolver.resolveSingletonOrFail(Application::class.java),
                resolver.resolveSingletonOrFail(UserInfoInterface::class.java),
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            SyncParticipant::class.java,
            "ticketmaster"
        ) { resolver -> resolver.resolveSingletonOrFail(TicketmasterManager::class.java) }
    }

    override fun afterAssembly(resolver: Resolver) {
        resolver.resolveSingletonOrFail(SyncCoordinatorInterface::class.java).registerParticipant(
            resolver.resolveSingletonOrFail(
                SyncParticipant::class.java,
                "ticketmaster"
            )
        )

        val analyticEventFilter = IntentFilter().apply {
            TMScreenActionToRoverNames.forEach { addAction(it.key) }
        }

        LocalBroadcastManager.getInstance(resolver.resolveSingletonOrFail(Application::class.java).applicationContext)
            .registerReceiver(TicketMasterAnalyticsBroadcastReceiver(), analyticEventFilter)
    }
}

@Deprecated("Use .resolve(TicketmasterAuthorizer::class.java)")
val RoverCampaigns.ticketmasterAuthorizer: TicketmasterAuthorizer
    get() = RoverCampaigns.sharedInstance.resolveSingletonOrFail(TicketmasterAuthorizer::class.java)
