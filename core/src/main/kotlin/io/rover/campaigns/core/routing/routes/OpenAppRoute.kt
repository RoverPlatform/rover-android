package io.rover.campaigns.core.routing.routes

import android.content.Intent
import io.rover.campaigns.core.routing.Route
import java.net.URI

class OpenAppRoute(
    private val openAppIntent: Intent?
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        // a null URI means merely open app, which means it should map to this route.
        return if (uri == null) {
            return openAppIntent
        } else null
    }
}
