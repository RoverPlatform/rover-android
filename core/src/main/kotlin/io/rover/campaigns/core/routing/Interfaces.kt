package io.rover.campaigns.core.routing

import android.content.Intent
import io.rover.campaigns.core.container.Assembler
import java.net.URI

interface Router {
    /**
     * Map the given [uri] to an Intent as per the relevant registered [Route].  If nothing in the
     * currently installed Rover SDK modules can provide an Intent for the URI.
     *
     * @param inbound If true, means the link is being executed by something outside of the Rover
     * SDK, that is, an intent arriving from Android.  If false, means the link is being emitted by
     * something within the Rover SDK, and that any URIs that fail to match any registered routes
     * should be deferred to Android itself.
     */
    fun route(uri: URI?, inbound: Boolean): Intent?

    /**
     * Register the given route.  Should typically be called in [Assembler.afterAssembly]
     * implementations.
     */
    fun registerRoute(route: Route)
}

interface Route {
    /**
     * Return an [Intent] for the given URI if this route is capable of handling it.
     *
     * Note; these do not check the Schema.
     */
    fun resolveUri(uri: URI?): Intent?
}

interface LinkOpenInterface {
    /**
     * Map a URI just received for a deep link to an explicit, mapped intent.
     */
    fun localIntentForReceived(receivedUri: URI): List<Intent>
}
