package io.rover.rover.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.Shader
import android.util.DisplayMetrics
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
import io.rover.rover.ui.types.PixelSize
import io.rover.rover.ui.types.Rect
import io.rover.rover.ui.types.RectF

/**
 * Exposed by a view model that may need to contribute to the padding around the content.
 */
interface LayoutPaddingDeflection {
    val paddingDeflection: Rect
}

/**
 * Specifies how the view should properly display the given background image.
 *
 * The method these are specified is a bit idiosyncratic on account of Android implementation
 * details and the combination of Drawables the view uses to achieve the effect.
 */
class BackgroundImageConfiguration(
    /**
     * Bounds in pixels, in *relative insets from their respective edges*.
     *
     * TODO: consider changing to not use Rect to better indicate that it is not a rectangle but an inset for each edge
     *
     * Our drawable is always set to FILL_XY, which means by specifying these insets you get
     * complete control over the aspect ratio, sizing, and positioning.  Note that this parameter
     * cannot be used to specify any sort of scaling for tiling, since the bottom/right bounds are
     * effectively undefined as the pattern repeats forever.  In that case, consider using using
     * [imageNativeDensity] to achieve a scale effect (although note that it is in terms of the
     * display DPI).
     *
     * (Note: we use this approach rather than just having a configurable gravity on the drawable
     * because that would not allow for aspect correct fit scaling.)
     */
    val insets: Rect,

    /**
     * An Android tiling mode.  For no tiling, set as null.
     */
    val tileMode: Shader.TileMode?,

    /**
     * This density value should be set on the bitmap with [Bitmap.setDensity] before drawing it
     * on an Android canvas.
     */
    val imageNativeDensity: Int
)

/**
 * This interface is exposed by View Models that have support for a background.  Equivalent to
 * the [Background] domain model interface.
 */
interface BackgroundViewModelInterface {
    val backgroundColor: Int

    fun requestBackgroundImage(
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics,
        callback: (
            /**
             * The bitmap to be drawn.  It is recommended that the consumer arrange to have it
             * scaled to a roughly appropriate amount (need not be exact; that is the purpose of the
             * view size and the [insets] given above) and also to be uploaded to GPU texture memory
             * off thread ([Bitmap.prepareToDraw]) before setting it.
             *
             * Note: one can set the source density of the bitmap to control its scaling (which is
             * particularly relevant for tile modes where
             */
            Bitmap,

            BackgroundImageConfiguration
        ) -> Unit
    ): NetworkTask?
}

/**
 * This interface is exposed by View Models that have support for a border (of arbitrary width and
 * possibly rounded with a radius).  Equivalent to the [Border] domain model interface.
 */
interface BorderViewModelInterface : LayoutPaddingDeflection {
    val borderColor: Int

    // TODO: this should start returning Px instead of Dp
    val borderRadius: Int

    // TODO: this should start returning Px instead of Dp
    val borderWidth: Int

    companion object
}

/**
 * View Model for a block that contains rich text content (decorated with strong, italic, and
 * underline HTML tags).
 */
interface TextViewModelInterface : Measurable {
    val text: String

    val fontAppearance: FontAppearance

    fun boldRelativeToBlockWeight(): Font
}

interface ImageViewModelInterface : Measurable {
    // TODO: I may elect to demote the Bitmap concern from the ViewModel into just the View (or a
    // helper of some kind) in order to avoid a thick Android object (Bitmap) being touched here

    /**
     * Get the needed image for display, hitting caches if possible and the network if necessary.
     * You'll need to give a [PixelSize] of the target view the image will be landing in.  This will
     * allow for optimizations to select, download, and cache the appropriate size of content.
     *
     * Remember to call [NetworkTask.resume] to start the retrieval, or your callback will never
     * be hit.
     */
    fun requestImage(
        targetViewPixelSize: PixelSize,
        displayMetrics: DisplayMetrics,
        callback: (Bitmap) -> Unit
    ): NetworkTask?
}

/**
 * Can vertically measure its content for stacked/autoheight purposes.
 */
interface Measurable {
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
