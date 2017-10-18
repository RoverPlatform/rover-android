package io.rover.rover.ui

import android.support.v7.widget.RecyclerView
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen
import io.rover.rover.core.logging.log
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
    private val screenViewModel: ScreenViewModelInterface
) : RecyclerView.LayoutManager() {
    private var layout : Layout by Delegates.notNull()

    // State:
    /**
     * The current scroll position of the recycler view (specifically, the top of the RecyclerView's
     * 'viewport' into the list contents).  This is kept as state because it is
     * ultimately the LayoutManager's responsibility to know the position.
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
        layout = screenViewModel.render(
            this.width
        )
        fill(recycler)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        // now we need to figure out how much we can scroll by, and if indeed dy would bring
        // us out-of-bounds and cap it.
        if (layout.height <= height) {
            // can't scroll at all; content shorter than the recycler view!
            return 0
        }

        // the LinearLayoutManager assumes that layouts basically trust & assume the layouts
        // set their own dimensions, like may be reasonably expected of standard Android views.
        // However, we have our rendered layout graph specify the dimensions in addition to the
        // positions........  this doesn't seem out of scope for a LayoutManager, though!
        // perhaps we should be calling measure for children (I think API probably requires it
        // anyway), but with our precomputed sizes just as bounds?

        // a few thoughts about Google's layout managers: they use LayoutState for both passing parameters and holding state! BLEH

        // it seems like the layout manager is responsible for keeping track of the position state?

        // it seems like we're not responsible for preventing scroll past the beginning, just the
        // end.

        log.v("current scrollPosition $scrollPosition")

        // deflect the state variable by the appropriate amount (taking into account the edges)
        val deflection = if (dy > 0) {
            // going down
            log.v("going down")
            if((scrollPosition + height + dy) > layout.height) {
                // would scroll past end of the content.
                log.v("now at bottom. scroll position: $scrollPosition")
                return (scrollPosition + height + dy) - layout.height
            } else {
                // a safe amount of scroll.
                dy
            }
        } else {
            // going up
            log.v("going up")
            if((scrollPosition + dy) <= 0) {
                // would scroll back past the beginning.
                0
            } else {
                dy
            }
        }

        // apply the vertical translation to all the currently live views
        // offsetChildrenVertical(deflection)

        log.v("Deflecting by: $deflection")

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

        // note: we infer a naturally increasing z-order.
        layout.coordinatesAndViewModels.forEachIndexed { index, (viewPosition, viewModel) ->
            val visible = viewPosition.bottom > verticalTopBound && viewPosition.top < verticalBottomBound

            if(visible) {
                val view = recycler.getViewForPosition(index)

                addView(view)

                view.measure(viewPosition.width(), viewPosition.height())

                layoutDecorated(
                    view,
                    viewPosition.left,
                    viewPosition.top - scrollPosition,
                    viewPosition.right,
                    viewPosition.bottom - scrollPosition
                )
            }
        }
    }
}
