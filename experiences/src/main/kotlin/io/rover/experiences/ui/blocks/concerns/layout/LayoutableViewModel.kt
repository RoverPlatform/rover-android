package io.rover.experiences.ui.blocks.concerns.layout

import io.rover.core.ui.RectF
import io.rover.experiences.ui.layout.ViewType
import io.rover.core.ui.concerns.BindableViewModel
import io.rover.experiences.ui.layout.BlockAndRowRecyclerAdapter
import io.rover.experiences.ui.layout.row.RowViewModel
import io.rover.experiences.ui.layout.screen.ScreenViewModel
import io.rover.core.ui.Rect

/**
 * Any View Model that implements this interface can be laid out in in the vertically
 * scrollable plane of a [ScreenViewModel].
 *
 * (View models that do not implement this interface are typically used in a compositional way
 * as a part of other view models that do implement [LayoutableViewModel].
 */
interface LayoutableViewModel : BindableViewModel {
    /**
     * Measures and returns a [RectF] of the placement the view model (with origin being the
     * bounds).
     *
     * Note that the returned rect is relative to the same space as the given [bounds]
     * rect, but not relative to the [bounds] rect itself.
     *
     * Note that some types (particularly, [RowViewModel] will not honour all the given
     * constraints, particularly on the [Rect.bottom] element of the bounds as they define
     * their own height or expand to accommodate measured content as per the stacking/auto-height
     * feature.
     */
    fun frame(bounds: RectF): RectF

    /**
     * There is a constrained set of classes that will implement [LayoutableViewModel].
     * [BlockAndRowRecyclerAdapter] will need to know which type is associated with a given
     * instance of [LayoutableViewModel] in its render list.
     */
    val viewType: ViewType
}
