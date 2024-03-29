/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.experiences.classic.layout

import android.util.DisplayMetrics
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import io.rover.sdk.core.data.domain.Block
import io.rover.sdk.core.data.domain.Row
import io.rover.sdk.core.data.domain.Screen
import io.rover.sdk.core.logging.log
import io.rover.sdk.experiences.classic.dpAsPx
import io.rover.sdk.experiences.classic.layout.screen.ScreenViewModelInterface
import io.rover.sdk.experiences.classic.pxAsDp

/**
 * A [RecyclerView.LayoutManager] that can position Rover UI elements (namely, all the [Row]s in
 * a [Screen] and the various varieties of [Block]s they can contain within a [RecyclerView]).
 *
 * Most of the heavy lifting is done in the implementations of the view models.  See
 * [ScreenViewModelInterface] for details.
 *
 * This layout manager is perhaps a bit unusual compared to the stock ones: it's driven by data.
 */
internal class BlockAndRowLayoutManager(
    private val layout: Layout,
    private val displayMetrics: DisplayMetrics
) : RecyclerView.LayoutManager() {
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

    init {
        isItemPrefetchEnabled = true
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.WRAP_CONTENT,
        RecyclerView.LayoutParams.WRAP_CONTENT
    )

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        // in typical RecyclerView LayoutMangers, this method would do the heavy lifting of building
        // the layout.  However, in our arrangement, that is done externally because we need the
        // full layout early.  So, we'll just invoke fill.

        val widthDp = this.width.pxAsDp(displayMetrics)

        if (layout.width != widthDp) {
            log.w("onLayoutChildren() noticed that current rendered layout is meant for view of width of ${layout.width} dp, but current view width is $widthDp dp.")
        }
        fill(recycler, layout)
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: androidx.recyclerview.widget.RecyclerView.State): Int {
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
        fill(recycler, layout)

        // let RecyclerView know how much we moved.
        return deflection
    }

    /**
     * In the midst of the scroll, if RecyclerView deems there's enough time remaining before
     * the frame must complete, it may ask us if there are any rows it can ask be to be created
     * and bound ahead of them coming on screen.
     *
     * Note that this does *not* handle prefetching rows that are merely some distance away:
     * rather, this is about trying to lead loading rows for a fling that is currently in progress.
     */
    override fun collectAdjacentPrefetchPositions(
        dx: Int,
        dy: Int,
        state: RecyclerView.State,
        layoutPrefetchRegistry: LayoutPrefetchRegistry
    ) {
        val wouldBeScrollPosition = scrollPosition + dy
        val verticalTopBound = wouldBeScrollPosition
        val verticalBottomBound = wouldBeScrollPosition + height

        // TODO: naturally this is a very slow method.  once we have a better data structure in use
        // in fill() to query for items based on vertical position, then we'll start using it here,
        // too.
        layout.coordinatesAndViewModels.forEachIndexed { index, (viewPosition, _, _) ->
            val displayPosition = viewPosition.dpAsPx(displayMetrics)
            val warmOver = displayPosition.bottom > (verticalTopBound) && displayPosition.top < (verticalBottomBound)
            if (warmOver) {
                layoutPrefetchRegistry.addPosition(index, Math.abs(displayPosition.top - scrollPosition))
            }
        }
    }

    /**
     * Ensure all views needed for the current [scrollPosition] are populated.
     */
    private fun fill(
        recycler: RecyclerView.Recycler,
        layout: Layout
    ) {
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
                    view.clipBounds = clipBounds.dpAsPx(displayMetrics).asAndroidRect()
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

                // In the event that any of the views are composite views (such as WebViews)
                // that need to know their size outside of the otherwise entirely decoupled-from-
                // Android Rover layout system, we set their Android layout parameters such that
                // they know what their dimensions are.
                view.layoutParams.width = displayPosition.width()
                view.layoutParams.height = displayPosition.height()

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
