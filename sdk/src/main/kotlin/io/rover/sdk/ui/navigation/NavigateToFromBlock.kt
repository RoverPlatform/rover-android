package io.rover.sdk.ui.navigation

import io.rover.sdk.data.domain.Block
import java.net.URI

/**
 * Should navigate to the given URL or Screen.
 */
sealed class NavigateToFromBlock(
    val block: Block
) {
    /**
     * Navigate to something external to the experience through the Rover URI [Router].
     */
    class External(
        val uri: URI,
        block: Block
    ) : NavigateToFromBlock(block)

    class GoToScreenAction(
        val screenId: String,
        block: Block
    ) : NavigateToFromBlock(block)

    class PresentWebsiteAction(
        val url: URI,
        block: Block
    ) : NavigateToFromBlock(block)
}
