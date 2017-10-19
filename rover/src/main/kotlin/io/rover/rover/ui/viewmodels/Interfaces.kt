package io.rover.rover.ui.viewmodels

import android.graphics.Rect
import android.graphics.RectF
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.Screen
import io.rover.rover.ui.BlockAndRowLayoutManager
import io.rover.rover.ui.types.Alignment
import io.rover.rover.ui.types.Insets
import io.rover.rover.ui.types.Layout

/**
 * This interface is exposed by View Models that have support for a background.  Equivalent to
 * the [Background] domain model interface.
 */
interface BackgroundViewModelInterface {
    val backgroundColor: Int
}

/**
 * A view model for Blocks (particularly, the dynamic layout thereof).
 */
interface BlockViewModelInterface : LayoutableViewModel {
    fun stackedHeight(bounds: RectF): Float

    val insets: Insets

    val isStacked: Boolean

    val opacity: Float

    val verticalAlignment: Alignment

    fun width(bounds: RectF): Float
}

/**
 * View model for Rover UI blocks.
 */
interface RowViewModelInterface : LayoutableViewModel, BackgroundViewModelInterface {
    fun blockViewModels(): List<BlockViewModelInterface>
}

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
    fun render(widthDp: Float): Layout

    /**
     * Retrieve a list of the view models in the order they'd be laid out (guaranteed to be in
     * the same order as returned by [render]), but without the layout itself being performed.
     */
    fun gather(): List<LayoutableViewModel>

    fun rowViewModels(): List<RowViewModelInterface>
}

interface RectangleBlockViewModelInterface: BlockViewModelInterface, BackgroundViewModelInterface



