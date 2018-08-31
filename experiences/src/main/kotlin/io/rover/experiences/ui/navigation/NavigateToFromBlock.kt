package io.rover.experiences.ui.navigation

import io.rover.core.data.domain.AttributeValue
import io.rover.core.routing.Router
import java.net.URI

/**
 * Should navigate to the given URL or Screen.
 */
sealed class NavigateToFromBlock(
    /**
     * An [AttributeValue] to describe the source block that this navigation event came from.
     */
    val blockAttributes: AttributeValue
) {
    /**
     * Navigate to something external to the experience through the Rover URI [Router].
     */
    class External(
        val uri: URI,
        blockAttributes: AttributeValue
    ) : NavigateToFromBlock(blockAttributes)

    class GoToScreenAction(
        val screenId: String,
        blockAttributes: AttributeValue
    ) : NavigateToFromBlock(blockAttributes)

    class PresentWebsiteAction(
        val url: URI,
        blockAttributes: AttributeValue
    ) : NavigateToFromBlock(blockAttributes)
}
