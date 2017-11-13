package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import io.rover.rover.core.domain.Background
import io.rover.rover.core.domain.Border
import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.Screen
import io.rover.rover.services.network.NetworkTask
import io.rover.rover.ui.BlockAndRowLayoutManager
import io.rover.rover.ui.types.Alignment
import io.rover.rover.ui.types.DisplayItem
import io.rover.rover.ui.types.Font
import io.rover.rover.ui.types.FontAppearance
import io.rover.rover.ui.types.Insets
import io.rover.rover.ui.types.Layout

/**
 * Exposed by a view model that may need to contribute to the padding around the content.
 */
interface LayoutPaddingDeflection {
    val paddingDeflection: Rect
}

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
interface BorderViewModelInterface : LayoutPaddingDeflection {
    val borderColor: Int

    val borderRadius: Int

    val borderWidth: Int

    companion object
}

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextViewModelInterface : Measureable {
    val text: String

    val fontAppearance: FontAppearance

    fun boldRelativeToBlockWeight(): Font
}

interface ImageViewModelInterface : Measureable {
    // TODO: I may elect to demote the Bitmap concern from the ViewModel into just the View (or a
    // helper of some kind) in order to avoid a thick Android object (Bitmap) being touched here

    /**
     * Get the needed image for display, hitting caches if possible and the network if necessary.
     *
     * Remember to call [NetworkTask.resume] to start the retrieval, or your callback will never
     * be hit.
     */
    fun requestImage(callback: (Bitmap) -> Unit): NetworkTask?
}

/**
 * Can vertically measure its content for stacked/autoheight purposes.
 */
interface Measureable {
    /**
     * Measure the "natural" height for the content contained in this block (for
     * example, a wrapped block of text will consume up to some height depending on content and
     * other factors), given the width of the bounds.  Used for our auto-height feature.
     */
    fun intrinsicHeight(bounds: RectF): Float
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

    /**
     * Alpha applied to the entire view.
     *
     * Between 0 (transparent) and 1 (fully opaque).
     */
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
interface RectangleBlockViewModelInterface : LayoutableViewModel, BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextBlockViewModelInterface : LayoutableViewModel, BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface, TextViewModelInterface

interface ImageBlockViewModelInterface : LayoutableViewModel, BlockViewModelInterface, BackgroundViewModelInterface, BorderViewModelInterface, ImageViewModelInterface
