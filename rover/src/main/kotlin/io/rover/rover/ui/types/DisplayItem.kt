package io.rover.rover.ui.types

import io.rover.rover.ui.viewmodels.LayoutableViewModel
import io.rover.rover.ui.viewmodels.ScreenViewModel

/**
 * A sequence of [LayoutableViewModel]s in two-dimensional space, with optional clips,
 * as an output of a layout pass.
 */
data class DisplayItem(
    /**
     * Where in absolute space (both position and dimensions) for the entire [ScreenViewModel]
     * this item should be placed.
     */
    val position: RectF,

    /**
     * An optional clip area, in the coordinate space of the view itself (ie., top left of the view
     * is origin).
     */
    val clip: RectF?,

    /**
     * The view model itself that originally supplied the layout parameters and must also be
     * bound to the View at display time to set styling and display content.
     */
    val viewModel: LayoutableViewModel
)
