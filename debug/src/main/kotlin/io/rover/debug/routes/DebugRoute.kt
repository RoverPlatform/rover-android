package io.rover.debug.routes

import android.content.Context
import android.content.Intent
import io.rover.debug.RoverDebugActivity
import io.rover.core.routing.Route
import io.rover.core.platform.whenNotNull
import java.net.URI

class DebugRoute(
    private val context: Context
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        return uri.whenNotNull {
            if (it.authority == "presentSettings") {
                Intent(
                    context,
                    RoverDebugActivity::class.java
                )
            } else null
        }
    }
}
