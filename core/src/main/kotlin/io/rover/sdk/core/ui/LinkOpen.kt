package io.rover.sdk.core.ui

import android.content.Intent
import io.rover.sdk.core.routing.LinkOpenInterface
import io.rover.sdk.core.routing.Router
import java.net.URI

class LinkOpen(
    private val router: Router
) : LinkOpenInterface {
    override fun localIntentForReceived(receivedUri: URI): List<Intent> {
        return listOfNotNull(router.route(receivedUri, true))
    }
}
