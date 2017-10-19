package io.rover.rover.ui

import android.graphics.Rect
import android.graphics.RectF
import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.logging.log
import io.rover.rover.ui.types.Layout
import io.rover.rover.ui.viewmodels.ScreenViewModelInterface
import kotlin.properties.Delegates

/**
 * A [RecyclerView.LayoutManager] that can position Rover UI elements (namely, all the [Row]s in
 * a [Screen] and the various varieties of [Block]s they can contain within a [RecyclerView]).
 *
 * Most of the heavy lifting is done in the implementations of the view models.  See
 * [ScreenViewModelInterface] for details.
 *
 * This layout manager is a bit unusual compared to the stock ones: it's driven by data.
 */
class BlockAndRowLayoutManager(
    private val screenViewModel: ScreenViewModelInterface,
    private val displayMetrics: DisplayMetrics
) : RecyclerView.LayoutManager() {
    private var layout : Layout by Delegates.notNull()

    // State:
    /**
     * The current scroll position of the recycler view (specifically, the top of the RecyclerView's
     * 'viewport' into the list contents).  This is kept as state because it is
     * ultimately the LayoutManager's responsibility to know the position.
     *
     * This value is in display-dependent pixels, not display-independent logical pixels.
     *
     * Note that this value is always 0 or more.
     *
     * TODO: how can this be persisted through configuration changes and thus all of these objects
     * getting recreated?  seems likely that remembering the top-most item and travelling back to
     * it is better.
     */
    private var scrollPosition : Int = 0

    override fun canScrollVertically(): Boolean = true

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.WRAP_CONTENT,
        RecyclerView.LayoutParams.WRAP_CONTENT
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        log.d("Expensive onLayoutChildren() called.")

        // call the expensive operation of having the screen view model (and nested view models)
        // lay out all of their view models into an abstract representation of their positions on the
        // display.  This also flattens out all the rows and blocks into a single dimensional
        // sequence.
        // We then persist this as in-memory state
        layout = screenViewModel.render(
            pxAsDp(this.width)
        )
        fill(recycler)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        // now we need to figure out how much we can scroll by, and if indeed dy would bring
        // us out-of-bounds and cap it.

        val layoutDisplayHeight = dpAsPx(layout.height)

        if (layoutDisplayHeight <= height) {
            // can't scroll at all; content shorter than the recycler view!
            return 0
        }

        // deflect the state variable by the appropriate amount (taking into account the edges)
        val deflection = if (dy > 0) {
            // going down
            if((scrollPosition + height + dy) > layoutDisplayHeight) {
                // would scroll past end of the content.
                // determine amount needed to scroll to absolute end, but no further.
                layoutDisplayHeight - height - scrollPosition
            } else {
                // a safe amount of scroll.
                dy
            }
        } else {
            // going up
            if((scrollPosition + dy) <= 0) {
                // would scroll back past the beginning.
                // instead determine the amount needed to go back to the absolute beginning, but no
                // further.
                0 - scrollPosition
            } else {
                dy
            }
        }

        // apply the vertical translation to all the currently live views
        // This will only be necessary when fill() is optimized to not scrap views unnecessarily.
        // offsetChildrenVertical(deflection)

        // Side-effect: update position state with the deflection.
        scrollPosition += deflection

        // and re-run the fill:
        fill(recycler)

        return deflection
    }

    /**
     * Ensure all views needed for the current [scrollPosition] are populated.
     */
    private fun fill(recycler: RecyclerView.Recycler) {
        // put all the views in scrap (for now; performance optimizations may be possible here, too?)
        detachAndScrapAttachedViews(recycler)

        val verticalTopBound = scrollPosition
        val verticalBottomBound = scrollPosition + height

        // now we iterate through the entire display list.
        //
        // TODO: (future optimization possible here; build a range data structure to enable us to only iterate over views likely to be relevant to
        // current display position)

        // note: we infer a naturally increasing z-order; Android treats order of addition as
        // z-order, and we process through our blocks-and-rows seequentially.
        layout.coordinatesAndViewModels.forEachIndexed { index, (viewPosition, viewModel) ->
            val displayPosition = viewPosition.dpAsPx()

            val visible = displayPosition.bottom > verticalTopBound && displayPosition.top < verticalBottomBound
            if(visible) {
                val view = recycler.getViewForPosition(index)
                addView(view)
                view.measure(displayPosition.width(), displayPosition.height())

                layoutDecorated(
                    view,
                    displayPosition.left,
                    displayPosition.top - scrollPosition,
                    displayPosition.right,
                    displayPosition.bottom - scrollPosition
                )
            }
        }
    }

    /**
     * Convert display-independent DP metrics to an appropriate value for this display.
     *
     * See "Converting DP Units to Pixel Units" on
     * https://developer.android.com/guide/practices/screens_support.html
     */
    private fun dpAsPx(dp: Float): Int {
        val scale = displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    /**
     * Convert display-independent DP metrics to an appropriate value for this display.
     *
     * See [Converting DP Units to Pixel Units](https://developer.android.com/guide/practices/screens_support.html)
     */
    private fun dpAsPx(dp: Int): Int {
        val scale = displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun pxAsDp(pixels: Int): Float {
        val scale = displayMetrics.density
        return pixels / scale
    }

    /**
     * Convert a [Rect] of display-independent DP metrics to an appropriate value for this display.
     *
     * See "Converting DP Units to Pixel Units" on
     * https://developer.android.com/guide/practices/screens_support.html
     */
    private fun RectF.dpAsPx(): Rect {
        return Rect(
            dpAsPx(left),
            dpAsPx(top),
            dpAsPx(right),
            dpAsPx(bottom)
        )
    }
}
