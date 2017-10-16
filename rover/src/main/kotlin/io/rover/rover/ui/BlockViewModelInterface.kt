package io.rover.rover.ui

import android.graphics.Rect

data class Insets(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int
)

enum class Alignment {
    Bottom,
    Fill,
    Center,
    Top
}

/**
 * A view model for Blocks (particularly, the dynamic layout thereof).
 */
interface BlockViewModelInterface : LayoutableViewModel {
    fun stackedHeight(bounds: Rect): Float

    val insets: Insets

    val isStacked: Boolean

    val opacity: Float

    val verticalAlignment: Alignment

    fun width(bounds: Rect): Float
}
