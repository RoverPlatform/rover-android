package io.rover.sdk.experiences

import android.app.Application
import android.content.Context
import io.rover.sdk.core.UrlSchemes
import io.rover.sdk.core.container.Assembler
import io.rover.sdk.core.container.Container
import io.rover.sdk.core.container.Resolver
import io.rover.sdk.core.container.Scope
import io.rover.sdk.core.data.graphql.GraphQlApiServiceInterface
import io.rover.sdk.core.events.ContextProvider
import io.rover.sdk.core.events.EventQueueServiceInterface
import io.rover.sdk.core.logging.log
import io.rover.sdk.core.platform.LocalStorage
import io.rover.sdk.core.routing.Router
import io.rover.sdk.experiences.events.contextproviders.ConversionsContextProvider
import io.rover.sdk.experiences.services.EventEmitter

/**
 * Location Assembler contains the Rover SDK subsystems for Geofence, Beacon, and location tracking.
 *
 * It can automatically use the Google Location services, which you can opt out of by passing false
 * to the following boolean parameters.  You may wish to do this if you want to use a Location SDK
 * from a vendor other than Google, integrate with your own location implementation, or do not
 * require the functionality.
 *
 * Note: if you use any of the below, then you must complete the Google Play Services setup as per
 * the SDK documentation (also needed for the Notifications module).
 */
class ExperiencesAssembler : Assembler {
    override fun assemble(container: Container) {
        container.register(
            Scope.Singleton,
            PresentExperienceIntents::class.java
        ) { resolver ->
            PresentExperienceIntents(
                resolver.resolveSingletonOrFail(Context::class.java)
            )
        }

        container.register(
            Scope.Singleton,
            ContextProvider::class.java,
            "conversions"
        ) { resolver ->
            ConversionsContextProvider(
                resolver.resolveSingletonOrFail(LocalStorage::class.java)
            )
        }
    }

    override fun afterAssembly(resolver: Resolver) {
        super.afterAssembly(resolver)
        resolver.resolveSingletonOrFail(Router::class.java).apply {
            val urlSchemes = resolver.resolveSingletonOrFail(UrlSchemes::class.java)
            registerRoute(
                PresentExperienceRoute(
                    urlSchemes = urlSchemes.schemes,
                    associatedDomains = urlSchemes.associatedDomains,
                    presentExperienceIntents = resolver.resolveSingletonOrFail(
                        PresentExperienceIntents::class.java
                    )
                )
            )
        }

        RoverExperiences.initialize(
                resolver.resolveSingletonOrFail(Application::class.java),
                resolver.resolveSingletonOrFail(String::class.java, "accountToken"),
                resolver.resolveSingletonOrFail(Int::class.java, "chromeTabBackgroundColor"),
                resolver.resolveSingletonOrFail(GraphQlApiServiceInterface::class.java)
        )

        /**
         * Attempt to retrieve the [EventEmitter] instance from the rover sdk in order to receive
         * events for analytics and automation purposes.
         */
        val eventEmitter = RoverExperiences.shared?.eventEmitter

        eventEmitter?.let {
            EventReceiver(
                resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java)
            ).startListening(it)

            (resolver.resolveSingletonOrFail(
                ContextProvider::class.java,
                "conversions"
            ) as ConversionsContextProvider).startListening(it)
        }
            ?: log.w("A Rover SDK event emitter wasn't available; Rover events will not be tracked.  Make sure you call Rover.initialize() before initializing the Rover SDK.")


        resolver.resolveSingletonOrFail(EventQueueServiceInterface::class.java).addContextProvider(
            resolver.resolveSingletonOrFail(ContextProvider::class.java, "conversions")
        )
    }
}
