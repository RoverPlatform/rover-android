package io.rover.rover.ui.types

/**
 * The output of a Rover layout pass.
 */
data class Layout(
    val coordinatesAndViewModels: CoordinatesAndViewModels,
    val height: Int
)