package io.rover.rover.ui.types

/**
 * The output of a Rover layout pass.
 */
data class Layout(
    val coordinatesAndViewModels: CoordinatesAndViewModels,

    /**
     * The total height (in dp) of the entire layout from start to finish.
     */
    val height: Float
)