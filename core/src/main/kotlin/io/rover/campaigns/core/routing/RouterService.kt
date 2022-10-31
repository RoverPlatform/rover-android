package io.rover.campaigns.core.routing

import android.content.Intent
import android.net.Uri
import io.rover.campaigns.core.logging.log
import java.net.URI

class RouterService(
    private val openAppIntent: Intent?
) : Router {
    private val registeredRoutes: MutableSet<Route> = mutableSetOf()

    override fun route(uri: URI?, inbound: Boolean): Intent? {
        val mappedUris = registeredRoutes.mapNotNull { it.resolveUri(uri) }

        if (mappedUris.size > 1) {
            log.w(
                "More than one Route matched the the given URI (`$uri`), resulting in the following intents: \n" +
                    "    -> ${mappedUris.joinToString { it.toString() }}\n"
            )
        }

        val handledByRover = mappedUris.firstOrNull()

        return when {
            handledByRover != null -> handledByRover
            (inbound || uri == null && openAppIntent != null) -> {
                log.w("No Route matched `$uri`, just opening the app.")
                openAppIntent!!
            }
            (inbound || uri == null && openAppIntent == null) -> {
                log.w("No Route matched `$uri` and openAppIntent null.")
                null
            }
            else -> {
                log.i("No built-in Rover Campaigns route matched `$uri`.  Opening it as an Intent.")
                Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
            }
        }
    }

    override fun registerRoute(route: Route) {
        registeredRoutes.add(route)
    }
}
