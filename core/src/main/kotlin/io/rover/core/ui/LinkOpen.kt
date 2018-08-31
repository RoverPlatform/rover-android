package io.rover.core.ui

import android.content.Intent
import io.rover.core.routing.LinkOpenInterface
import io.rover.core.routing.Router
import java.net.URI

class LinkOpen(
    private val router: Router
) : LinkOpenInterface {
    override fun localIntentForReceived(receivedUri: URI): List<Intent> {
        return listOf(router.route(receivedUri, true))
    }
}
