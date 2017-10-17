package io.rover.rover.ui

import android.graphics.Rect
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.Screen

/**
 * View Model for a Screen.  Used in [Experience]s.
 *
 * Rover View Models are a little atypical compared to what you may have seen elsewhere in
 * industry: unusually, layouts are data, so much layout structure and parameters are data
 * passed through and transformed by the view models.
 *
 * Implementers can take a comprehensive UI layout contained within
 * a Rover [Screen], such as that within an Experience, and lay all of the contained views out
 * into two-dimensional space.  It does so by mapping a given [Screen] to an internal graph of
 * [RowViewModelInterface]s and [BlockViewModelInterface]s, ultimately yielding the [RowViewModelInterface]s and
 * [BlockViewModelInterface]s as a sequence of [LayoutableViewModel] flat blocks in two-dimensional space.
 *
 * Primarily used by [BlockAndRowLayoutManager].
 */
interface ScreenViewModelInterface {
    /**
     * Do the computationally expensive operation of laying out the entire graph of UI view models.
     */
    fun render(widthDp: Int): Layout

    fun rowViewModels(): List<RowViewModelInterface>
}

/**
 * A sequence of [LayoutableViewModel]s in two-dimensional space.
 */
typealias CoordinatesAndViewModels = List<Pair<Rect, LayoutableViewModel>>

data class Layout(
    val coordinatesAndViewModels: CoordinatesAndViewModels,
    val height: Int
)