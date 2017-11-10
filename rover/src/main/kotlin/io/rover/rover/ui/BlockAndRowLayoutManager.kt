package io.rover.rover.ui

import android.support.v7.widget.RecyclerView
import android.util.DisplayMetrics
import android.view.View
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.logging.log
import io.rover.rover.ui.types.Layout
import io.rover.rover.ui.types.dpAsPx
import io.rover.rover.ui.types.pxAsDp
import io.rover.rover.ui.viewmodels.ScreenViewModelInterface
import kotlin.properties.Delegates

/**
 * A [RecyclerView.LayoutManager] that can position Rover UI elements (namely, all the [Row]s in
 * a [Screen] and the various varieties of [Block]s they can contain within a [RecyclerView]).
 *
 * Most of the heavy lifting is done in the implementations of the view models.  See
 * [ScreenViewModelInterface] for details.
 *
 * This layout manager is perhaps a bit unusual compared to the stock ones: it's driven by data.
 */
class BlockAndRowLayoutManager(
    private val screenViewModel: ScreenViewModelInterface,
    private val displayMetrics: DisplayMetrics
) : RecyclerView.LayoutManager() {
    private var layout: Layout by Delegates.notNull()

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
    private var scrollPosition: Int = 0

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
            this.width.pxAsDp(displayMetrics)
        )
        fill(recycler)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        // now we need to figure out how much we can scroll by, and if indeed dy would bring
        // us out-of-bounds and cap it.

        val layoutDisplayHeight = layout.height.dpAsPx(displayMetrics)

        if (layoutDisplayHeight <= height) {
            // can't scroll at all; content shorter than the recycler view!
            return 0
        }

        // deflect the state variable by the appropriate amount (taking into account the edges)
        val deflection = if (dy > 0) {
            // going down
            if ((scrollPosition + height + dy) > layoutDisplayHeight) {
                // would scroll past end of the content.
                // determine amount needed to scroll to absolute end, but no further.
                layoutDisplayHeight - height - scrollPosition
            } else {
                // a safe amount of scroll.
                dy
            }
        } else {
            // going up
            if ((scrollPosition + dy) <= 0) {
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

        // let RecyclerView know how much we moved.
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

        // TODO: future optimization possible here: build a range data structure to enable us to
        // only iterate over views likely to be relevant to current display position)

        // TODO: additional future optimization possible here: only add and remove changed rows,
        // translate the rest. this will avoid re-measuring every frame and whatnot rather than
        // relying on the scrap, and then use offsetChildrenVertical().

        // note: we infer a naturally increasing z-order; Android treats order of addition as
        // z-order, and we process through our blocks-and-rows sequentially.
        layout.coordinatesAndViewModels.forEachIndexed { index, (viewPosition, clipBounds, _) ->
            val displayPosition = viewPosition.dpAsPx(displayMetrics)

            val visible = displayPosition.bottom > verticalTopBound && displayPosition.top < verticalBottomBound
            if (visible) {
                // retrieve either a newly recycled view, or perhaps, get the exact same view back

                val view = recycler.getViewForPosition(index)

                // TODO: to avoid expensive re-clipping we may want to tag Views that are clipped
                // differently so as to recycle the ones with the same clip. indeed,
                // re-clipping is being unnecessarily done for every frame while the view is
                // onscreen because we are scrapping and re-adding everything (the range
                // optimization spoken about above will help here)

                view.clipBounds = null
                if (clipBounds != null) {
                    view.clipBounds = clipBounds.dpAsPx(displayMetrics)
                }

                // TODO: when implementing the aforementioned additional future optimization, the natural
                // viewmodel order will no longer carry through to the order of the addView() calls
                // below (for example, scrolling upwards and adding views at the top).  This means
                // it will be necessary to build & maintain stateful data
                // structure that allows for fast lookup of view iteration number (ie., i in
                // 0..n hits where i is an index into live on-screen rows
                // that are added by addView() below in a given pass of fill()) to view that should
                // be drawn. This would then be quickly queried by setChildDrawingOrderCallback().
                // More concretely: an array of views ordered by draw order that RecyclerView would
                // be able to check at draw-time to allow us to control the view *draw* order
                // independently of view add order.

                addView(view)

                view.measure(
                    View.MeasureSpec.makeMeasureSpec(
                        displayPosition.width(),
                        View.MeasureSpec.EXACTLY
                    ),
                    View.MeasureSpec.makeMeasureSpec(
                        displayPosition.height(),
                        View.MeasureSpec.EXACTLY
                    )
                )

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
}
