package io.rover.core.ui.navigation

import io.rover.core.data.domain.Attributes
import java.net.URI

/**
 * Should navigate to the given URL or Screen.
 */
sealed class NavigateToFromBlock(
    /**
     * An [AttributeValue] to describe the source block that this navigation event came from.
     */
    val blockAttributes: Attributes
) {
    /**
     * Navigate to something external to the experience through the Rover URI [Router].
     */
    class External(
        val uri: URI,
        blockAttributes: Attributes
    ) : NavigateToFromBlock(blockAttributes)

    class GoToScreenAction(
        val screenId: String,
        blockAttributes: Attributes
    ) : NavigateToFromBlock(blockAttributes)

    class PresentWebsiteAction(
        val url: URI,
        blockAttributes: Attributes
    ) : NavigateToFromBlock(blockAttributes)
}
