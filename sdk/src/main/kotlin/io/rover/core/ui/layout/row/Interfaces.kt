package io.rover.core.ui.layout.row

import io.rover.core.ui.RectF
import io.rover.core.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.core.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.core.ui.layout.DisplayItem
import io.rover.core.ui.navigation.NavigateToFromBlock
import org.reactivestreams.Publisher

/**
 * View model for Rover UI blocks.
 */
interface RowViewModelInterface : LayoutableViewModel, BackgroundViewModelInterface {
    val blockViewModels: List<BlockViewModelInterface>

    /**
     * Render all the blocks to a list of coordinates (and the [BlockViewModelInterface]s
     * themselves).
     */
    fun mapBlocksToRectDisplayList(
        rowFrame: RectF
    ): List<DisplayItem>

    /**
     * Rows may emit navigation events.
     */
    val eventSource: Publisher<Event>

    /**
     * Does this row contain anything that calls for the backlight to be set temporarily extra
     * bright?
     */
    val needsBrightBacklight: Boolean

    data class Event(
        val blockId: String,
        val navigateTo: NavigateToFromBlock
    )
}
