package io.rover.notifications.routing.routes

import android.content.Intent
import io.rover.core.logging.log
import io.rover.core.routing.Route
import java.net.URI

class PresentNotificationCenterRoute(
    private val urlSchemes: List<String>,
    private val notificationCenterIntent: Intent?
) : Route {
    override fun resolveUri(uri: URI?): Intent? {
        return if (urlSchemes.contains(uri?.scheme) && uri?.authority == "presentNotificationCenter") {
            notificationCenterIntent.apply {
                if (this == null) log.w("Notification Open intent needed, but one was not specified to NotificationsAssembler.")
            }
        } else null
    }
}
