package io.rover.rover.ui.viewmodels

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.Border
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.Screen
import io.rover.rover.ui.BlockAndRowLayoutManager
import io.rover.rover.ui.types.Alignment
import io.rover.rover.ui.types.DisplayItem
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
 * This interface is exposed by View Models that have support for a border (of arbitrary width and
 * possibly rounded with a radius).  Equivalent to the [Border] domain model interface.
 */
interface BorderViewModelInterface {
    val borderColor: Int

    val borderRadius: Int

    val borderWidth: Int

    companion object
}

/**
 * A view model for Blocks (particularly, the dynamic layout thereof).
 */
interface BlockViewModelInterface : LayoutableViewModel {
    /**
     * The full amount contributed by this block (including its own height and offsets) to the
     * height of all the stacked blocks within the row.  So, the subsequent stacked block must be
     * laid out at a y position that is the sum of all the [stackedHeight]s of all the prior stacked
     * blocks.
     */
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
    val blockViewModels: List<BlockViewModelInterface>

    /**
     * Render all the blocks to a list of coordinates (and the [BlockViewModelInterface]s
     * themselves).
     */
    fun mapBlocksToRectDisplayList(
        rowFrame: RectF
    ): List<DisplayItem>
}

/**
 * View Model for a Screen.  Used in [Experience]s.
 *
 * Rover View Models are a little atypical compared to what you may have seen elsewhere in industry:
 * unusually, layouts are data, so much layout structure and parameters are data passed through and
 * transformed by the view models.
 *
 * Implementers can take a comprehensive UI layout contained within a Rover [Screen], such as that
 * within an Experience, and lay all of the contained views out into two-dimensional space.  It does
 * so by mapping a given [Screen] to an internal graph of [RowViewModelInterface]s and
 * [BlockViewModelInterface]s, ultimately yielding the [RowViewModelInterface]s and
 * [BlockViewModelInterface]s as a sequence of [LayoutableViewModel] flat blocks in two-dimensional
 * space.
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

/**
 * View Model for a block that contains no content (other than its own border and
 * background).
 */
interface RectangleBlockViewModelInterface: BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextBlockViewModelInterface: BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface {
    val text: String

    val fontFace: FontFace
}

// TODO: move to types package




// this is viewmodel->view type.
data class FontFace(
    /**
     * Font size, in Android Scalable Pixels.
     */
    val fontSize: Int,

    /**
     * An Android style value of either [Typeface.NORMAL], [Typeface.BOLD], [Typeface.ITALIC], or
     * [Typeface.BOLD_ITALIC].
     */
    val fontStyle: Int,

    /**
     * A font family name.
     */
    val fontFamily: String,

    /**
     * An ARGB color value suitable for use with various Android APIs.
     */
    val color: Int,

    val align: Paint.Align
)
