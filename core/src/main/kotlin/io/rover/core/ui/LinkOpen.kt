package io.rover.core.ui

import android.content.Intent
import io.rover.core.routing.LinkOpenInterface
import io.rover.core.routing.Router
import java.net.URI

class LinkOpen(
    private val router: Router,
    /**
     * Rover deep links are customized for each app in this way:
     *
     * rv-myapp://...
     *
     * You must select an appropriate slug without spaces or special characters to be used in place
     * of `myapp` above.  You must also configure this in your Rover settings TODO explain how
     *
     * You should also consider adding the handler to the manifest.  While this is not needed for
     * any Rover functionality to work, it is required for clickable deep/universal links to work from
     * anywhere else. TODO explain how once the stuff to do so is built
     */
    deepLinkSchemeSlug: String
): LinkOpenInterface {

    init {
        // validate the deep link slug and ensure it's sane.
        when {
            deepLinkSchemeSlug.isBlank() -> throw RuntimeException("Deep link scheme slug must not be blank.")
            deepLinkSchemeSlug.startsWith("rv-") -> throw RuntimeException("Do not include the `rv-` prefix to your deep link scheme slug.  That is added for you.")
            deepLinkSchemeSlug.contains(" ") -> throw RuntimeException("Deep link scheme slug must not contain spaces.")
            // TODO: check for special characters.
        }
    }

    override fun localIntentForReceived(receivedUri: URI): List<Intent> {
        return listOf(router.route(receivedUri, true))
    }
}
