package io.rover.rover.ui.types

/**
 * The output of a Rover layout pass.
 */
data class Layout(
    /**
     * All of the items that must be displayed, all laid out into a coordinate space (constrained
     * by a width that was given to the rendering process that yielded this [Layout]).
     *
     * The items are given in an increasing z-order: items that come later in the list must occlude
     * any items that they conflict with that came before in the list.
     */
    val coordinatesAndViewModels: List<DisplayItem>,

    /**
     * The total height (in dp) of the entire layout from start to finish, computed as a result
     * of the layout pass.
     */
    val height: Float,

    /**
     * The requested width for the layout pass.
     */
    val width: Float
)
