package io.rover.experiences.ui.navigation

import io.rover.core.routing.Router
import java.net.URI

/**
 * Should navigate to the given URL or Screen.
 */
sealed class NavigateTo {
    /**
     * Navigate to something external to the experience through the Rover URI [Router].
     */
    class External(
        val uri: URI
    ): NavigateTo()

    class GoToScreenAction(
        val screenId: String
    ) : NavigateTo()

    class PresentWebsiteAction(
        val url: URI
    ) : NavigateTo()
}
