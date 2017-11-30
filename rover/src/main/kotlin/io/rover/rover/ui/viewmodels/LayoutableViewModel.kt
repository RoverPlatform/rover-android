package io.rover.rover.ui.viewmodels

import io.rover.rover.ui.types.RectF
import io.rover.rover.ui.types.ViewType

/**
 * Any View Model that implements this interface can be laid out in in the vertically
 * scrollable plane of a [ScreenViewModel].
 *
 * (View models that do not implement this interface are typically used in a compositional way
 * as a part of other view models that do implement [LayoutableViewModel].
 */
interface LayoutableViewModel {
    // TODO: in here go parameters common to the display of both Rows and Blocks.  So, possibly,
    // insets, backgrounds, and colours.  Basically, these ViewModels will
    // wrap the [Background] and [Border] model interfaces.

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
